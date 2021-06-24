(ns com.computesoftware.fluxjure.formatters
  (:require
    [clojure.string :as str]
    [com.computesoftware.fluxjure.data-readers :as readers])
  (:import (java.time Instant Duration)
           (java.util Date)
           (com.computesoftware.fluxjure.data_readers DataLiteral)))

(defprotocol IFormattable
  (stringify [x]))

(comment
  (sci/eval-string
    "({:a 1} :a)"
    {:bindings {'inc (fn [x] #(+ 2 x))
                'get (fn [_ _] "a")}
     :allow    '[inc]
     :features #{}})
  )

(defn quoted
  [x]
  (format "\"%s\"" x))

(defn as-iso-str
  [x]
  (str (Instant/ofEpochMilli (inst-ms x))))

(declare to-string)

(defn format-kvs
  [arg-map {:keys [compile-form] :as ctx}]
  (cond
    (map? arg-map)
    (str/join
      ", "
      (map (fn [[k v]]
             (format "%s: %s" (name k) (compile-form v ctx)))
        arg-map))
    (nil? arg-map)
    ""
    :else
    (throw (ex-info (str "Expected arg map, got " (pr-str (type arg-map)) ".")
             {:arg-map arg-map}))))

(comment
  (format-kvs {:bucket "test"})
  )

(defn format-record
  [record ctx]
  (format "{%s}" (format-kvs record ctx)))

(defn format-list
  [l {:keys [compile-form] :as ctx}]
  (format "[%s]" (str/join "," (map #(compile-form % ctx) l))))

(defn format-anon-fn
  [form {:keys [compile-form] :as ctx}]
  (let [[_ arg-vec & body] form
        bc (count body)
        argv-s (str/join ", " arg-vec)]
    (cond
      (= 0 bc)
      (format "(%s) => {}" argv-s)
      (= 1 bc)
      (format "(%s) => %s" argv-s (compile-form (first body) ctx))
      (> bc 1)
      (format "(%s) => {\n%s\nreturn %s\n}"
        argv-s
        (str/join "\n" (map #(compile-form % ctx) (butlast body)))
        (compile-form (last body) ctx)))))

(comment
  (format-anon-fn
    '(fn [r]
       (and (== (r "field") ":avail"))))
  (format-anon-fn
    '(fn []
       1
       #_(and (== (r "field") ":avail")))
    {:compile-form clj-flux.api/compile-form})
  )

(defn format-def
  [[_ name value] {:keys [compile-form] :as ctx}]
  (format "%s = %s" name (compile-form value ctx)))

(comment
  (format-def
    '(def x #inst"2020"))
  )

(defn format-option
  [[_ sym value] ctx]
  (format "option %s" (format-def (list 'def sym value) ctx)))

(defn format-import
  [[_ import-str] ctx]
  (format "import \"%s\"" import-str))

(defn format-let
  [[_ bindings & body] {:keys [compile-form] :as ctx}]
  (let [r (compile-form
            (concat
              (list 'fn [])
              (map (fn [[vname val]] (list 'def vname val)) (partition-all 2 bindings))
              body)
            ctx)]
    (when r (format "%s()" r))))

(comment
  (format-let
    '(let [x 1
           y 2]
       (+ x y))
    {:compile-form clj-flux.api/compile-form})
  )

(defn format-do
  [[_ & body] {:keys [compile-form] :as ctx}]
  (let [forms (map #(compile-form % ctx) body)]
    (str/join "\n" forms)))

;; https://github.com/influxdata/flux/blob/master/docs/SPEC.md#conditional-expressions
(defn format-if
  [[_ pred then else] {:keys [compile-form] :as ctx}]
  (format "if %s then %s%s"
    (compile-form pred {})
    (compile-form then {})
    (if else (str " else " (compile-form else {})) "")))

(defn format-with
  [[_ parent & kvs] {:keys [compile-form] :as ctx}]
  (format "%s with %s"
    parent
    (format-kvs (apply hash-map kvs) ctx)))

(defn format-pipe-forward
  [[_ & args] {:keys [compile-form] :as ctx}]
  (str/join "\n|> " (map #(compile-form % ctx) args)))

(defn format-function-call
  [[_ arg-map] {:keys [sym] :as ctx}]
  (format "%s(%s)"
    (let [ns (namespace sym)
          name (name sym)]
      (if ns (str ns "." name) name))
    (format-kvs arg-map ctx)))

(comment
  (println (format-function-call '(from {:bucket "test"})))
  )

(defn format-infix
  [[operator & args] {:keys [compile-form] :as ctx}]
  (str
    "("
    (str/join
      (format " %s " operator)
      (map #(compile-form % ctx) args))
    ")"))

(defn format-minus
  [[_ & args :as form] {:keys [compile-form] :as ctx}]
  (if (= 1 (count args))
    (format "-%s" (compile-form (first args) ctx))
    (format-infix form ctx)))

(comment
  (format-infix '(and (== (r "field") ":avail")))
  )

(defn with-op
  [[_ & body] new-op]
  (list* new-op body))

(defn format-with-op-fn
  [fmt-fn new-op]
  (fn [form ctx]
    (fmt-fn (with-op form new-op) ctx)))

(defn format-object-lookup
  [[_ obj-name & path] {:keys [compile-form] :as ctx}]
  (str obj-name
    (str/join
      (map (fn [p]
             (format "[%s]" (compile-form p ctx)))
        path))))

(comment
  (format-object-lookup
    "r"
    ["a" "b"])
  )

(let [w-nanos (.toNanos (Duration/ofDays 7))
      d-nanos (.toNanos (Duration/ofDays 1))
      h-nanos (.toNanos (Duration/ofHours 1))
      m-nanos (.toNanos (Duration/ofMinutes 1))
      s-nanos (.toNanos (Duration/ofMillis 1000))
      ms-nanos (.toNanos (Duration/ofMillis 1))
      ns-nanos 1
      units [[w-nanos "w"]
             [d-nanos "d"]
             [h-nanos "h"]
             [m-nanos "m"]
             [s-nanos "s"]
             [ms-nanos "ms"]
             [ns-nanos "ns"]]]
  (defn fmt-duration
    [^Duration x]
    (.toString
      (first
        (reduce
          (fn [[s-acc nanos :as acc] [unit-nanos suffix]]
            (let [u (quot nanos unit-nanos)
                  next-d (- nanos (* u unit-nanos))]
              (if (zero? u)
                acc
                [(-> s-acc (.append u) (.append suffix))
                 next-d])))
          [(StringBuilder.) (.toNanos x)] units)))))

(comment
  (fmt-duration2
    (Duration/ofMillis -1500))
  )

(extend-protocol IFormattable
  String
  (stringify [x] (quoted x))
  Date
  (stringify [x] (as-iso-str x))
  Instant
  (stringify [x] (as-iso-str x))
  Duration
  (stringify [^Duration x] (fmt-duration x))
  java.util.regex.Pattern
  (stringify [re]
    (format "/%s/" (str re)))
  DataLiteral
  (stringify [s] (:value s))
  nil
  (stringify [_]
    "null"))
