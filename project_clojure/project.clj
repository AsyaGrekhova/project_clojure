(defproject my-project "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :source-paths ["src"]
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [ring/ring-core "1.9.3"]
                 [ring/ring-jetty-adapter "1.9.3"]
                 [ring "1.9.3"]
                 [org.clojure/core.async "1.5.648"]
                 [clj-http "3.9.1"]
                 [cheshire "5.8.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [ring/ring-json "0.5.0"]
                 [org.clojure/tools.logging "1.1.0"]
                 [http-kit "2.5.0"]
                 [org.slf4j/slf4j-log4j12 "1.7.32"]
                 [org.slf4j/slf4j-api "1.7.32"]
                 [log4j/log4j "1.2.17"]
                 [org.slf4j/slf4j-simple "1.7.36"]
                 [midje "1.9.9"]]
  :repl-options {:init-ns my-project.core}
  :java-source-paths ["src/java"]
  :main my-project.server)