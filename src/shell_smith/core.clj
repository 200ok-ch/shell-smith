; -*- mode: clojure -*-

(ns shell-smith.core
  (:require [clojure.walk :as walk]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [docopt.core :as docopt]))

(defn config-from-file [path]
  (when (fs/exists? path)
    (-> path slurp yaml/parse-string)))

(defn config-from-env [pattern]
  (let [env (System/getenv)
        kees (filter (partial re-find pattern) (keys env))
        t (fn [[k v]] [(-> k (str/replace pattern "") str/lower-case keyword) v])]
    (into {} (map t (select-keys env kees)))))

(defn- not-reducer [a [k v]]
  (if-not v a (assoc a k v)))

(def ^:private drop-boring
  (partial reduce not-reducer {}))

(defn- simplify-key [key]
  (str/replace key #"(^--|[<>])" ""))

(defn- simplify-keys [opts]
  (let [t (fn [[k v]] [(simplify-key k) v])]
    (into {} (map t opts))))

(defn config-from-cli [usage args]
  (docopt/docopt usage
                 args
                 (comp drop-boring
                       walk/keywordize-keys
                       simplify-keys)))

(defmacro config [usage# & {:as opts#}]
  `(let [config-defaults#       (get ~opts# :defaults)
         cli-name#              (get ~opts# :name ~(str *ns*))
         config-file-config#    (str (System/getenv "XDG_CONFIG_HOME") "/" cli-name# "/config.yml")
         config-file-oldschool# (str (System/getenv "HOME") "/." cli-name# ".yml")
         config-file-local#     (str (fs/cwd) "/" cli-name# ".yml")
         env-var-prefix#        (str/upper-case cli-name#)
         env-var-regexp#        (re-pattern (str "^" env-var-prefix# "_"))
         args#                  (get ~opts# :args *command-line-args*)]
     (merge config-defaults#
            (config-from-file config-file-config#)
            (config-from-file config-file-oldschool#)
            (config-from-file config-file-local#)
            (config-from-env env-var-regexp#)
            (config-from-cli ~usage# args#))))

#_(macroexpand-1 '(shell-smith "hello"))
