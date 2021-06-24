(ns com.computesoftware.fluxjure.data-readers)

(defrecord DataLiteral [value])

;; e.g., InfluxDB Duration literal format
;; https://github.com/influxdata/flux/blob/master/docs/SPEC.md#duration-literals
(defn read-literal
  [s]
  (map->DataLiteral {:value s}))


