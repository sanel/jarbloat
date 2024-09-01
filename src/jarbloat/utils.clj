(ns jarbloat.utils
  (:require [clojure.string :as s]))

(defmacro if-graalvm
  "Execute blocks if graalvm is detected."
  ([then else]
   ;; "graalvm.compiler" is set through leiningen profile during compilation, but
   ;; "com.oracle.graalvm.isaot" can be emitted by graal in some versions AFAIK
   (if (or (System/getProperty "graalvm.compiler")
           (System/getProperty "com.oracle.graalvm.isaot"))
     `(do ~then)
     `(do ~else)))
  ([then] `(if-graalvm ~then nil)))

(defn path-drop-last
  "Drop last item in path and re-create path by using join-token."
  ([^String path join-token]
   (if (and path (.contains path "/"))
     (->> (.split ^String path "/") butlast (s/join join-token))
     path))
  ([path] (path-drop-last path "/")))

(defn pp-bytes
  "Pretty print byte value."
  [n]
  (let [k 1024
        sizes ["B" "KB" "MB" "GB" "TB"]
        i (Math/floor (/ (Math/log n) (Math/log k)))]
    (try
      ;; For sufficiently large number that goes out of 'sizes'
      ;; this will throw IndexOutOfBoundsException. In that case, handle it
      ;; and return raw value in bytes.
      (format "%.2f%s" (/ n (Math/pow k i)) (nth sizes i))
      (catch IndexOutOfBoundsException _
        (str n "B")))))
