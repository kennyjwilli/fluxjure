(ns com.computesoftware.fluxjure.test
  (:import (java.io InputStreamReader BufferedReader OutputStreamWriter BufferedWriter)
           (java.lang ProcessBuilder$Redirect)))


(comment
  (def pb (ProcessBuilder. ["bin/flux" "repl"]))
  (def pb (ProcessBuilder. ["less"]))
  (.inheritIO pb)
  (.redirectOutput pb ProcessBuilder$Redirect/INHERIT)
  (.redirectError pb ProcessBuilder$Redirect/INHERIT)
  (def proc (.start pb))
  (.exitValue proc)

  (def proc-in (.getOutputStream proc))
  (def proc-out (.getInputStream proc))

  (def reader (BufferedReader. (InputStreamReader. proc-out)))
  (.ready reader)
  (loop [cs []]
    (if (.ready reader)
      (let [c (.read reader)]
        (if (= -1 c)
          (println (apply str cs))
          (recur (conj cs (char c)))))
      (println (apply str cs))))

  (def writer (BufferedWriter. (OutputStreamWriter. proc-in)))
  (.write writer "2")
  (.newLine writer)
  (.flush writer)
  (.close writer)

  (.inheritIO pb))
