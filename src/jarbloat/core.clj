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
      "Usage: " cmd " [options] jar1 jar2...\n"
      "Examine jar file(s) and determine to which dependencies contribute to the bloated size.\n"
      "\n"
      "Usage:\n"
      "  -h, --help                               show this help\n"
      "  -v, --version                            show version\n"
      "\n"
      "  -a, --analyzer=[fast|bcel]               analyzer used for class files (default 'fast')\n"
      "\n"
      "  -s, --sort=[name|package|size|csize]     sort type (default 'size')\n"
      "      --sort-asc                           sort in ascending order\n"
      "      --group-ns                           group by namespace (default no, but always true when --output-type=html)\n"
      "      --group-package                      equivalent to --group-ns\n"
      "      --demunge                            try to demunge/demangle Clojure names\n"
      "      --pp-sizes                           pretty-print size in B/KB/MB/GB\n"
      "\n"
      "      --include-ns=pattern                 show only namespaces matching regex pattern\n"
      "      --include-package=pattern            equivalent to --include-ns\n"
      "      --exclude-ns=pattern                 exclude namespaces matching regex pattern\n"
      "      --exclude-package=pattern            equivalent to --exclude-ns\n"
      "\n"
      "  -t, --output-type=[table|csv|json|html]  report type (default is 'table')\n"
      "  -o, --output=file                        where report to 'file' (make sense when only a single jar is analyzed)\n"
      "  -d, --output-dir=dir                     write reports to 'dir' (useful if you analyze multiple jars)\n"
      "\n"
      "  -e, --deps                               calculate jar dependencies for every class and output it in graphviz dot format\n"
      "                                           (use with --group-ns/--group-package option to show only package dependencies)\n"
      "      --dependencies                       equivalent to --deps\n"
      "\n"
      "Report bugs to: https://github.com/sanel/jarbloat/issues"))))

(defn- value-of
  "Get a value from OptionSet by name. If multi? is true, get multiple
values, expecting argument to be used multiple times."
  ([mp ^OptionSet parser key ^String name multi? validator-fn]
   (if (.has parser name)
     (let [v (if multi?
               (into [] (.valuesOf parser name))
               (.valueOf parser name))]
       ;; allow validator-fn to accept sequence as argument for checking
       ;; so it can see if there are duplicate entries, e.g. --include=foo --include=foo
       ;; it will receive (validator-fn [foo foo])
       ;;
       ;; Also, if (validator-fn) returns false/nil, consider that as a failure too. If (validator-fn)
       ;; returns a different value, use it in argumens. Useful to get (re-pattern) without compiling
       ;; regex again.
       (if (fn? validator-fn)
         (when-let [v (validator-fn v)]
           (assoc mp key v))
         (assoc mp key v)))
     mp))
  ([mp parser key name multi?]
   (value-of mp parser key name multi? nil)))

(defn- valid-pattern? [p]
  (if (sequential? p)
    ;; if any of elements is nil (failed pattern), mark everything as false
    (let [v (map valid-pattern? p)]
      (when-not (some nil? v)
        v))
    (try
      (re-pattern p)
      (catch Exception e
        (printf "Bad regex pattern: %s\n%s\n" p (.getMessage e))))))

(defn- handle-args [args]
  (let [op (doto (OptionParser.)
             #_(.recognizeAlternativeLongOptions true)
             (.acceptsAll ["h" "help"])
             (.acceptsAll ["v" "version"])
             (.acceptsAll ["group-ns" "group-package"])
             (.acceptsAll ["sort-asc"])
             (.acceptsAll ["demunge" "demangle"])
             (.acceptsAll ["pp-sizes"])
             (.acceptsAll ["e" "deps" "dependencies"])
             (-> (.acceptsAll ["a", "analyzer"]) .withRequiredArg)
             (-> (.acceptsAll ["o" "output"]) .withRequiredArg)
             (-> (.acceptsAll ["t" "output-type"]) .withRequiredArg)
             (-> (.acceptsAll ["d" "output-dir"]) .withRequiredArg)
             (-> (.acceptsAll ["s" "sort"]) .withRequiredArg)
             (-> (.acceptsAll ["include-ns" "include-package"]) .withRequiredArg)
             (-> (.acceptsAll ["exclude-ns" "exclude-package"]) .withRequiredArg))
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
                        (-> {:group-ns  (.has st "group-ns")
                             :sort-asc  (.has st "sort-asc")
                             :demunge   (.has st "demunge")
                             :pp-sizes  (.has st "pp-sizes")
                             :deps      (.has st "deps")}
                            (value-of st :analyzer "analyzer" false)
                            (value-of st :sort "sort" false)
                            (value-of st :include-ns "include-ns" true valid-pattern?)
                            (value-of st :exclude-ns "exclude-ns" true valid-pattern?)
                            (value-of st :output "output" false)
                            (value-of st :output-type "output-type" false)
                            (value-of st :output-dir "output-dir" false)))
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
      (println "Got exception")
      (.printStackTrace e))))
