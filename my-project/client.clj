(ns my-project.client
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clj-http.client :as client]
            [clojure.core.async :as async :refer [<! >! go chan]]
            [clojure.core.async :refer [chan go <! >! close!]]
            [my-project.server :refer [-main]])) 

(-main)

(import '[java.net Socket])

(def servers-file "servers.edn")

(defn get-available-servers []
  (try
    (when (.exists (io/file servers-file))
      (let [servers (edn/read-string (slurp servers-file))]
        (println "Available servers:" servers)
        servers))
    (catch Exception e
      (println "Failed to read servers list:" (.getMessage e))
      [])))

(defn is-server-active? [server]
  (try
    (with-open [socket (Socket. "localhost" (:port server))]
      true)
    (catch Exception e
      false)))

(defn choose-server [operation]
  (let [servers (filter is-server-active? (get-available-servers))]
    (if (empty? servers)
      (throw (Exception. "No available servers."))
      (let [min-load (apply min (map :load servers)) ; Находим минимальную нагрузку
            active-servers (filter #(= (:load %) min-load) servers)] ; Фильтруем серверы с минимальной нагрузкой
        (rand-nth active-servers))))) ; Случайный выбор из серверов с минимальной нагрузкой


(defn serialize-java-object-to-string [obj]
  (json/generate-string obj))

(defn submit-job [func args clojure-code java-object]
  (let [server (choose-server func)]
    (if server
      (let [url (str "http://localhost:" (:port server))
            request-body (json/generate-string
                          (cond-> {}
                            func (assoc :remote-func (name func) :args args)
                            clojure-code (assoc :clojure-code clojure-code)
                            java-object (assoc :java-object (serialize-java-object-to-string java-object))))
            response-chan (chan)]
        (go
          (let [response (<! (async/thread
                               (client/post url
                                            {:body request-body
                                             :headers {"Content-Type" "application/json"}})))]
            (if (= 200 (:status response))
              (let [result (json/parse-string (:body response) true)]
                (when result
                  (println "Result:" result))
                (>! response-chan result) ;; Отправляем результат в канал
                (close! response-chan))  ;; Закрываем канал после отправки результата
              (do
                (println "Failed to submit job:" (:status response) (:body response))
                (>! response-chan nil) ;; Отправляем nil в канал в случае ошибки
                (close! response-chan))) ;; Закрываем канал
            ))
        response-chan)
      (do
        (println "No server selected. Job not submitted.")
        nil))))

(defn register-remote-function [name code]
  (let [server (choose-server nil)]
    (if server
      (try
        (let [url (str "http://localhost:" (:port server) "/register-function")
              request-body (json/generate-string {:name name :code code})]
          (println "Registering function..." name)
          (let [response (client/post url
                                      {:body request-body
                                       :headers {"Content-Type" "application/json"}})]
            (if (= 200 (:status response))
              (println "Function registered:" name)
              (println "Failed to register function:" (:status response) (:body response)))))
        (catch Exception e
          (println "Connection error:" (.getMessage e))))
      (println "No server selected. Function not registered."))))

;; Пример использования
(register-remote-function "subtract" "(fn [a b] (- a b))") ;; Регистрация функции вычитания

;; Параллельное выполнение map
(defn parallel-map [func args]
  (let [results (atom (vec (repeat (count args) nil)))
        jobs (chan)]
    (doseq [idx (range (count args))]
      (let [arg (nth args idx)]
        (go
          (let [result (<! (submit-job func [arg arg] nil nil))] ;; Передаем два аргумента
            (when result
              (swap! results assoc idx (:result result))
              (>! jobs :done))))))
    ;; Ждем завершения всех задач
    (go
      (dotimes [_ (count args)]
        (<! jobs))
      @results)))

;; Пример отправки Clojure-кода
(defn submit-clojure-code-example []
  (let [code "(+ 10 2)"]
    (println "Submitting Clojure code...")
    (let [result (submit-job nil nil code nil)]
      (when result
        (println "Result of executing Clojure code:" result)))))

;; Пример отправки Java-объекта
(defn submit-java-object-example []
  (let [person {:name "Alice" :age 30}]
    (println "Submitting Java object...")
    (let [result (submit-job nil nil nil person)]
      (when result
        (println "Result of sending Java object:" result)))))

(defn submit-remote-function-example []
  (let [func 'add
        args [6 7]]
    (println "Submitting remote function...")
    (let [result (submit-job func args nil nil)])))

;; Пример использования parallel-map
(defn submit-parallel-map-example []
  (let [func 'add
        args [1 2 3 4 5]]
    (println "Submitting parallel map...")
    (let [results (parallel-map func args)]
      (println "Results of parallel map:" results))))

;; Пример отправки удаленной функции
(defn submit-remote-function-example_new []
  (let [func 'subtract
        args [17 7]]
    (println "Submitting remote function...")
    (let [result (submit-job func args nil nil)]
      (when result
        (println "Result of remote function:" (:result result))))))

(submit-clojure-code-example)
(submit-java-object-example)
(submit-remote-function-example)
(submit-parallel-map-example)
(submit-remote-function-example_new)


;; Пример отправки параллельных задач для большого числа серверов

(defn start-three-servers-sequentially []
  (dotimes [_ 3]
    (-main))) 

(start-three-servers-sequentially) ;; запускаем три сервера


(defn submit-parallel-jobs-example []
  (let [func 'add
        args [1 2 3 4 5 6 7 8]] ;; Передаем отдельные числа
    (println "Submitting parallel jobs to multiple servers...")
    (let [results (parallel-map func args)]
      (println "Results of parallel jobs:" results))))

(submit-parallel-jobs-example) 

