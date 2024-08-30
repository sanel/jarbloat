(ns jarbloat.core
  (:gen-class)
  (:require [jarbloat.utils :refer [if-graalvm]]
            [jarbloat.analyzer :refer [analyze-jars]])
  (:import [joptsimple OptionParser OptionSet]))

(defmacro app-version
  "Read version from leiningen project. Expanded in compile-time."
  []
  `~(System/getProperty "jarbloat.version"))

;; OptionParser can display help by calling (.printHelpOn op *out*) and will
;; display descriptions from (.acceptsAll [..] "description"), and to compile it in
;; graalvm image, it needs to be passed these options:
;;
;;   -H:IncludeResources=joptsimple/ExceptionMessages.properties
;;   -H:IncludeResources=joptsimple/HelpFormatterMessages.properties
;;
;; However, I find manual formatting more flexible, so I'm keeping the things this way for now.
(defn- help [^OptionParser _]
  (let [cmd (if-graalvm "jarbloat" "java -jar jarbloat.jar")]
    (println
     (str
      "Usage: " cmd " [options] jar1 jar2..."
      "\n"
      "Examine jar file(s) and determine to which dependencies contribute to the bloated size.\n"
      "\n"
      "Usage:\n"
      " -h, --help                               Show this help\n"
      " -v, --version                            Show version\n"
      "\n"
      " -a, --analyzer=[fast|bcel]               Analyzer used for class files (default fast)\n"
      "\n"
      " -s, --sort=[uncompressed|compressed]     Sort type (default 'uncompressed')\n"
      "     --sort-asc                           Sort in ascending order (default is descending)\n"
      "     --group-ns                           Group by namespace (default no, but always true when --output-type=html)\n"
      "     --group-package                      Same as --group-ns\n"
      "     --demunge=[true|false]               Try to demunge/demangle clojure names (default true)\n"
      "     --pp-sizes=[true|false]              Pretty-print size bytes to B/KB/MB (default true)\n"
      "\n"
      " -t, --output-type=[table|json|html]      Report type (default is table)\n"
      " -o, --output=file                        Where to write output. Default is stdout\n"
      "\n"
      "Report bugs to: https://github.com/sanel/jarbloat/issues"))))

(defn- value-of [mp ^OptionSet parser key ^String name]
  (if (.has parser name)
    (assoc mp key (.valueOf parser name))
    mp))

(defn- handle-args [args]
  (let [op (doto (OptionParser.)
             #_(.recognizeAlternativeLongOptions true)
             (.acceptsAll ["h" "help"])
             (.acceptsAll ["v" "version"])
             (.acceptsAll ["group-ns" "group-package"])
             (.acceptsAll ["sort-asc"])
             (-> (.acceptsAll ["demunge" "demangle"]) .withRequiredArg)
             (-> (.acceptsAll ["a", "analyzer"]) .withRequiredArg)
             (-> (.acceptsAll ["o" "output"]) .withRequiredArg)
             (-> (.acceptsAll ["t" "type"]) .withRequiredArg)
             (-> (.acceptsAll ["pp-sizes"]) .withRequiredArg)
             (-> (.acceptsAll ["s" "sort"]) .withRequiredArg))
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
        (if (seq files)
          (analyze-jars files
                        (-> {:group-ns (.has st "group-ns")
                             :sort-asc (.has st "sort-asc")}
                            (value-of st :demunge "demunge")
                            (value-of st :analyzer "analyzer")
                            (value-of st :output "output")
                            (value-of st :type "type")
                            (value-of st :sort "sort")
                            (value-of st :pp-sizes "pp-sizes")))
          (println "No jar files specified in command line"))))))

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
      (println "Got exception: " (.getMessage e))
      (.printStackTrace e))))
