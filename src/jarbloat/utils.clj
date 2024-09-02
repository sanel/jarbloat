(ns jarbloat.utils
  (:require [clojure.string :as s])
  (:import java.util.regex.Pattern))

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

(defn path-cut
  "Take input a string with delimiter (in clojure regex form), split it
and drop last item. Later, join elements with join-delim. Useful for cutting
last elements of path or package name."
  ([^String s re join-token]
   (assert (instance? Pattern re) "re is expected to be a regex pattern")
   (assert (instance? String join-token) "join-token is expected to be a string")
   (let [t (and s (s/split s re))]
     ;; (count nil) -> 0
     (if (> (count t) 1)
       (s/join join-token (subvec t 0 (- (count t) 1)))
       s)))
  ([s join-token] (path-cut s #"/" join-token))
  ([s] (path-cut s "/")))

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

(defn update-with-keys
  "Similar to clojure.core/update, but apply f to multiple keys.
Just like clojure.core/update, it will throw NullPointerException if one of key from ks
is not present in a map."
  [m ks f]
  (reduce #(update %1 %2 f) m ks))
