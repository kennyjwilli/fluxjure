(ns com.computesoftware
  (:require
    [clj-flux.operators :as ops]
    [clj-flux.formatters :as formatters])
  (:import (java.time Duration)))

(defn compile-form
  [form ctx]
  (prn 'compile form (list? form) (type form))
  (cond
    (seq? form)
    (let [[sym & args] form
          ctx (assoc ctx
                :compile-form compile-form
                :sym sym)]
      (if-let [{::ops/keys [fmt]} (ops/getop sym)]
        (fmt form ctx)
        (formatters/format-function-call form ctx)))
    (satisfies? formatters/IFormattable form)
    (formatters/stringify form)
    (map? form)
    (formatters/format-record form ctx)
    (vector? form)
    (formatters/format-list form ctx)
    :else form))


(comment

  (compile-form
    '(-> (from {:bucket "test" :foo {:a "a"}})
       (range {:start #inst"2020" :end #inst"2020"})
       (filter {:fn (fn [r]
                      (and (!= (get r "field") ":avail")
                        (or (== (get r "field") ":avail2")
                          (== (get r "field") ":avail2"))))})
       (yield))
    {})

  (println
    (compile-form
      '(let [x (-> (from {:bucket "test"})
                 (range {:start #inst"2020" :end #inst"2020"})
                 (filter {:fn (fn [r]
                                (and (!= (get r "field") ":avail")
                                  (or (== (get r "field") ":avail2")
                                    (== (get r "field") ":avail2"))
                                  (matches (get r "cpu") #"cpu[0-2]")))}))]
         x)
      {}))

  (compile-form
    '(let [x {}]
       (with x :a 1))
    {})

  "from(bucket:\"example-bucket\")
  |> range(start:-1h)
  |> filter(fn:(r) =>
            r._measurement == \"cpu\" and
            r.cpu == \"cpu-total\"
  )
  |> aggregateWindow(every: 1m, fn: mean)"

  (def high (-> (from {:bucket "candles"})
              (range {:start (v timeRangeStart) :stop (v timeRangeStop)})
              (filter {:fn (fn [r] (== exchange (:exchange r)))})))

  (println
    (to-string
      '(-> (from {:bucket "example-bucket"})
         (range {:start (Duration/ofHours -1)})
         (filter {:fn (fn [r]
                        (and (== (get r "_measurement") "cpu")
                          (== (get r "_field") "usage_system")
                          (== (get r "cpu") "cpu-total")))})
         (window {:every (Duration/ofMinutes 5)})
         (mean)
         (duplicate {:column "_stop" :as "_time"})
         (window {:every "inf"}))))
  )

