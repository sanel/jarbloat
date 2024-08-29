(ns jarbloat.core
  (:gen-class)
  (:require [jarbloat.utils :refer [if-graalvm]]
            [jarbloat.analyzer :refer [analyze-jars]])
  (:import joptsimple.OptionParser))

(defmacro app-version
  "Read version from leiningen project. Expanded in compile-time."
  []
  `~(System/getProperty "jarbloat.version"))

;; OptionParser can display help by calling (.printHelpOn op *out*) and will
;; display descriptions from (.acceptsAll [..] "description"), but this doesn't work well
;; with graalvm. joptsimple bundles help formatter locales in jar/resource and those
;; are not accessible by graalvm compiler. Instead of that, just use simple hardcoded help.
(defn- help [^OptionParser op]
  (println
   (str
    (if-graalvm
      "Usage: jarbloat [options]"
      "Usage: java -jar jarbloat.jar [options] jar1 jar2...")
    "\n"
    "Examine jar file(s) and determine to which dependencies contribute to the bloated size.\n\n"
    "Usage:\n"
    " -h, --help                                  Show this help\n"
    " -v, --version                               Show version\n"
    "     --demunge    [true|false]               Try to demunge/demangle clojure names (default true)\n"
    "\n"
    " -s, --sort       [uncompressed|compressed]  Sort type (default 'uncompressed')\n"
    "     --group-ns                              Group by namespace\n"
    "\n"
    "     --pp-sizes                              Pretty-print size bytes to B/KB/MB\n"
    "\n"
    " -o, --output     [table|csv|json|image]     Output format (default is table)\n"
    "     --image-dir  dir                        Location where to put images (default current dir)\n"
    "Report bugs to: https://github.com/sanel/jarbloat/issues")))

(defn- handle-args [args]
  (let [op (doto (OptionParser.)
             (.acceptsAll ["h" "help"])
             (.acceptsAll ["v" "version"])
             (.acceptsAll ["demunge" "demangle"])
             (.acceptsAll ["group-ns"])
             (-> (.acceptsAll ["s" "sort"]) .withOptionalArg))
        ;; these are non-arg options (those not starting with '-') and are
        ;; consider as jar files that are going to be read
        nonopts (.nonOptions op)
        st (.parse op (into-array String args))]
    (cond
      (or (empty? args) (.has st "help"))
      (help op)

      (.has st "version")
      (println (app-version))

      ;; non-terminating arguments
      :else
      (let [;; get rest of the arguments considering them as a jar files
            files   (into [] (.values nonopts st))]
        (analyze-jars files
                      (cond-> {:demunge  (.has st "demunge")
                               :group-ns (.has st "group-ns")}
                        (.has st "sort") (assoc :sort (.valueOf st "sort"))))))))

(defn -main [& args]
  (try
    (handle-args args)
    ;; (future) will block until all threads are properly finished.
    ;; At this point, everything should be completed, so shutdown things explicitly.
    ;; More here: https://stackoverflow.com/a/31606470
    ;;
    ;; Also, trick with (bound? #'*1) is to check if code is running in REPL or not
    ;; as clojure REPL will define *1, *2 variables. This will prevent calling shutdown-agents
    ;; in REPL, which will kill REPL server.
    (when-not (bound? #'*1)
      (shutdown-agents))
    (catch Throwable e
      (println "Got exception: " (.getMessage e)))))
