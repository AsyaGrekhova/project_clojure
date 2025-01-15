(ns my-project.server
  (:require [clojure.core.async :refer [chan go timeout <!]]
            [cheshire.core :as json]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :as resp]
            [ring.util.response :refer [response]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clj-http.client :as client])
  (:import (ch.qos.logback.classic Level)
           (org.slf4j LoggerFactory)))

(def servers-file "servers.edn")
(def jobs (atom []))
(def job-status (atom {}))
(def remote-functions (atom {}))

(defn call-remote-function [name args]
  (if-let [f (get @remote-functions name)]
    (apply f args)
    (throw (Exception. (str "Function not found: " name)))))

(defn register-job [job-id job]
  (swap! jobs conj job)
  (swap! job-status assoc job-id {:status "registered"})
  (println "Registered job:" job-id "Status: registered"))

(defn update-job-status [job-id status]
  (swap! job-status assoc job-id {:status status})
  (println "Updated job:" job-id "Status:" status))

(defn ensure-servers-file []
  (when-not (.exists (io/file servers-file))
    (spit servers-file "[]")
    (println "Created servers file at:" servers-file)))

(defn update-server-load [server-id delta]
  (ensure-servers-file)
  (let [servers (edn/read-string (slurp servers-file))
        updated-servers (mapv (fn [s]
                                (if (= (:id s) server-id)
                                  (update s :load + delta)
                                  s))
                              servers)]
    (spit servers-file (pr-str updated-servers))
    (println "Updated server load for" server-id "by" delta)))

(defn register-remote-function [name f]
  (swap! remote-functions assoc name f)
  (println "Registered remote function:" name)
  (println "Current remote functions:" @remote-functions)) ;; Добавлено для отладки)

(defn register-function-handler [req server-id]
  (let [body (slurp (:body req))
        func-data (json/parse-string body true)
        func-name (:name func-data)
        func-code (:code func-data)
        job-id (java.util.UUID/randomUUID)]

    (println "Function data:" func-data) ;; Отладка
    (println "Function name:" func-name) ;; Отладка
    (println "Function code:" func-code) ;; Отладка

    (try
      (let [func (eval (read-string func-code))]
        (register-remote-function (symbol func-name) func)
        (update-job-status job-id "registered")
        (response (json/generate-string {:status "Function registered" :name func-name})))
      (catch Exception e
        (println "Failed to register function:" (.getMessage e))
        (update-job-status job-id "failed")
        (response (json/generate-string {:error (.getMessage e)}))))))


(defn check-function-handler [req]
  (println "Received request:" req) ;; Отладка
  (let [func-name (get-in req [:query-params "func"])] 
    (println "Checking function name:" func-name) ;; Отладка
    (if (nil? func-name)
      (response (json/generate-string {:available false :error "Function name cannot be nil"}))
      (let [func-symbol (symbol func-name)] 
        (if (contains? @remote-functions func-symbol)
          (response (json/generate-string {:available true}))
          (response (json/generate-string {:available false :error "Function not found"})))))))



(defn job-handler [req server-id]
  (let [uri (:uri req)]
    (if (= uri "/register-function")
      (register-function-handler req server-id)
      ;; Остальная логика
      (let [body (slurp (:body req))
            job (json/parse-string body true)
            remote-func (:remote-func job)
            java-object (:java-object job)
            args (:args job)
            clojure-code (:clojure-code job)
            job-id (java.util.UUID/randomUUID)]

        (register-job job-id job)
        (update-server-load server-id 1)

        (try
          (let [result (cond
                         remote-func (call-remote-function (symbol remote-func) args)
                         clojure-code (eval (read-string clojure-code))
                         java-object (let [parsed-java-object (json/parse-string java-object true)]
                                       (str "Java object - Name: " (:name parsed-java-object) ", Age: " (:age parsed-java-object)))
                         :else (throw (Exception. "Invalid job type")))]
            (update-server-load server-id -1)
            (response (json/generate-string {:result result})))
          (catch Exception e
            (update-server-load server-id -1)
            (println "Job failed:" job-id "Error:" (.getMessage e))
            (update-job-status job-id "failed")
            (response (json/generate-string {:error (.getMessage e)}))))))))


(defn configure-logging []
  (let [logger (LoggerFactory/getLogger "org.eclipse.jetty")]
    (.setLevel logger Level/WARN)))

(defn start-server []
  (let [server-id (str (java.util.UUID/randomUUID))
        keep-alive-ch (chan)
        server (run-jetty
                (fn [req]
                  (cond
                    (= (:uri req) "/check-function") (check-function-handler req)
                    (= (:uri req) "/keep-alive") (do
                                                   (println "Received keep-alive signal")
                                                   (update-server-load server-id -1)
                                                   (response (json/generate-string {:status "OK"})))
                    :else (job-handler req server-id)))
                {:port 0 :join? false})
        port (-> server .getConnectors first .getLocalPort)]

    (ensure-servers-file)
    (let [servers (edn/read-string (slurp servers-file))
          server-info {:id server-id :port port :load 0}]
      (spit servers-file (pr-str (conj servers server-info)))
      (println "Server registered with ID:" server-id "on port:" port))

    ;; Start a background task to send keep-alive signals
    (go
      (loop []
        (<! (timeout (* 30 60 1000))) ;; Wait for 30 minutes
        (client/get (str "http://localhost:" port "/keep-alive")) ;; Send keep-alive signal
        (recur)))

    server))


(defn add [a b]
  (+ a b))

(defn register-remote-functions []
  (register-remote-function 'add add))


 
(defn -main [& args]
  (configure-logging)
  (register-remote-functions)
  (start-server))

(-main)



