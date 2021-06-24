(ns com.computesoftware.fluxjure.operators
  (:refer-clojure :exclude [-> fn and or == * + - / get get-in let mod < > <= >=])
  (:require
    [clojure.spec.alpha :as s]
    [com.computesoftware.fluxjure.formatters :as formatters]))

;; Operators
;; https://docs.influxdata.com/influxdb/v2.0/reference/flux/language/operators/

(s/def ::defop-args (s/cat
                      :docstring (s/? string?)
                      :format-fn any?))

(alias 'core 'clojure.core)

(defn make-op
  [sym fmt-fn]
  {::sym sym
   ::fmt fmt-fn})

(def ops (atom {}))

(defn add-op!
  [op-map]
  (swap! ops assoc (::sym op-map) op-map)
  (::sym op-map))

(defmacro defop
  {:arglists '([name docstring? fmt-fn])}
  [name & options]
  (core/let [{:keys [docstring format-fn] :as c} (s/conform ::defop-args options)]
    `(core/let [op-map# (make-op '~name ~format-fn)]
       (add-op! op-map#))))

(defn getop
  [op-sym]
  (core/get @ops op-sym))

(defop -> #'formatters/format-pipe-forward)
(defop fn #'formatters/format-anon-fn)
(defop def #'formatters/format-def)
(defop option #'formatters/format-option)
(defop with #'formatters/format-with)

(defop let #'formatters/format-let)
(defop do #'formatters/format-do)
(defop if #'formatters/format-if)

(defop import #'formatters/format-import)

;; Comparison Operators
;; https://docs.influxdata.com/influxdb/v2.0/reference/flux/language/operators/#comparison-operators
(defop == #'formatters/format-infix)
(defop != #'formatters/format-infix)
(defop < #'formatters/format-infix)
(defop > #'formatters/format-infix)
(defop <= #'formatters/format-infix)
(defop >= #'formatters/format-infix)

;; Logical Operators
;; https://docs.influxdata.com/influxdb/v2.0/reference/flux/language/operators/#logical-operators
(defop and #'formatters/format-infix)
(defop or #'formatters/format-infix)
(defop exists #'formatters/format-infix)

;; Arithmetic operators
;; https://docs.influxdata.com/influxdb/v2.0/reference/flux/language/operators/#arithmetic-operators
(defop + #'formatters/format-infix)
(defop - #'formatters/format-minus)
(defop * #'formatters/format-infix)
(defop 'Math/exp (formatters/format-with-op-fn #'formatters/format-infix "^"))
(defop mod (formatters/format-with-op-fn #'formatters/format-infix '%))
(defop matches (formatters/format-with-op-fn #'formatters/format-infix "=~"))
(defop !matches (formatters/format-with-op-fn #'formatters/format-infix "!~"))


(defop get #'formatters/format-object-lookup)
(defop get-in #'formatters/format-object-lookup)
