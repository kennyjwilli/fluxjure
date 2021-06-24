(ns com.computesoftware.fluxjure
  (:require
    [com.computesoftware.fluxjure.data-readers]
    [com.computesoftware.fluxjure.operators :as ops]
    [com.computesoftware.fluxjure.formatters :as formatters])
  (:import (java.time Duration)))

(defn flux
  ([form] (flux form nil))
  ([form ctx]
   ;(prn 'compile form (list? form) (type form))
   (cond
     (seq? form)
     (let [[sym & args] form
           ctx (assoc ctx
                 :compile-form flux
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
     :else form)))

'(do
   (import "influxdata/influxdb")
   (import "slack")
   (def slackWebhook "https://hooks.slack.com/services/XXXX/XXXX/XXXXX")
   (def slackChannel "##")
   (def cardinalityThreshold 1000000)
   (def alert
     (fn [bucketCard bucketName]
       (if (>= bucketCard cardinalityThreshold)
         (fn []
           (slack/message {:url     slackWebhook
                           :channel slackChannel
                           :text    "${bucketName} cardinality at ${string(v: bucketCard)}!"
                           :color   "warning"}))
         (fn [] 0))))
   (-> (buckets)
     (map {:fn (fn [r]
                 (let [cards (-> (influxdb/cardinality
                                   {:start  (- (get task "every"))
                                    :bucket (get r "name")})
                               (findRecord {:idx 0
                                            :fn  (fn [key] true)}))
                       result (alert {:bucketCard (get cards "_value")
                                      :bucketName (get r "name")})]
                   (with r :card (get cards "_value") :sent (result))))})))

(comment
  (do
    (require 'sc.api)
    (defn pflux [x] (println (flux x))))

  (pflux
    '(-> (from {:bucket "example-bucket"})
       (range {:start #fluxjure/lit"-1h"})
       (filter {:fn (fn [r]
                      (and
                        (== (get r "_measurement") "cpu")
                        (== (get r "cpu") "cpu-total")))})
       (aggregateWindow {:every #fluxjure/lit"-1m"
                         :fn    mean})))


  (pflux
    '(let [x (-> (from {:bucket "test"})
               (range {:start #inst"2020" :end #inst"2020"})
               (filter {:fn (fn [r]
                              (and (!= (get r "field") ":avail")
                                (or (== (get r "field") ":avail2")
                                  (== (get r "field") ":avail2"))
                                (matches (get r "cpu") #"cpu[0-2]")))}))]
       x))

  ;; copied from
  ;;https://github.com/influxdata/community-templates/blob/adb5c7cad12e195baa587ae123bdac7ec8cc0f3f/influxdb2_operational_monitoring/influxdb2_cardinality_now.yml#L21-L53
  (pflux
    '(do
       (import "influxdata/influxdb")
       (import "slack")
       (def slackWebhook "https://hooks.slack.com/services/XXXX/XXXX/XXXXX")
       (def slackChannel "##")
       (def cardinalityThreshold 1000000)
       (def alert
         (fn [bucketCard bucketName]
           (if (>= bucketCard cardinalityThreshold)
             (fn []
               (slack/message {:url     slackWebhook
                               :channel slackChannel
                               :text    "${bucketName} cardinality at ${string(v: bucketCard)}!"
                               :color   "warning"}))
             (fn [] 0))))
       (-> (buckets)
         (map {:fn (fn [r]
                     (let [cards (-> (influxdb/cardinality
                                       {:start  (- (get task "every"))
                                        :bucket (get r "name")})
                                   (findRecord {:idx 0
                                                :fn  (fn [key] true)}))
                           result (alert {:bucketCard (get cards "_value")
                                          :bucketName (get r "name")})]
                       (with r :card (get cards "_value") :sent (result))))}))))

  (flux
    '(let [x {}]
       (with x :a 1)))

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

