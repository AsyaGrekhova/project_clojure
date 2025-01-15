(ns my-project.client_test
  (:require [clojure.test :refer :all]
            [my-project.client :as client]
            [clojure.core.async :refer [<! >! go chan close!]]
            [clj-http.client :as http]
            [cheshire.core :as json]))

(defn mock-server []
  ;; Мокируем сервер для тестирования
  (let [server (atom {})]
    (reset! server {:port 3000})
    server))

;; тест на возвращение доступных серверов

(deftest test-get-available-servers
  (with-redefs [slurp (constantly "[{:id 1 :port 3000 :load 0}]")]
    (let [servers (client/get-available-servers)]
      (is (= 1 (count servers)))
      (is (= 3000 (:port (first servers)))))))

;; тест на проверку корректности отправления задачи на сервер и получения ответа

(deftest test-submit-job
  (let [response-chan (chan)]
    (with-redefs [http/post (fn [_ _] {:status 200 :body (json/generate-string {:result 42})})
                  client/choose-server (constantly {:id 2 :port 3001})]
      (go
        (let [result (<! (client/submit-job 'add [1 2] nil nil))]
          (is (= 200 (:status result)))
          (is (= 42 (:result (json/parse-string (:body result) true)))))))))

;; тест на выбор свободного и активного сервера

(deftest test-choose-server-with-load
  (let [servers [{:id 1 :port 3000 :load 1}  ;; Занятый
                 {:id 2 :port 3001 :load 1}  ;; Занятый
                 {:id 3 :port 3002 :load 0}]] ;; Свободный
    (with-redefs [client/get-available-servers (constantly servers)
                  client/is-server-active? (fn [server]
                                             (not (= (:load server) 1)))]
      (let [server (client/choose-server 'add)]
        (is (= 3002 (:port server))) ;; Ожидаем, что выберется свободный сервер
        (is (= 3 (:id server)))))))

;; тест на проверку корректности выполнения заданной функции на разных серверах

(deftest test-parallel-job-distribution
  (let [response-chan (chan)
        servers [{:id 1 :port 3000 :load 0}
                 {:id 2 :port 3001 :load 0}
                 {:id 3 :port 3002 :load 0}]]
    (with-redefs [client/get-available-servers (constantly servers)
                  client/is-server-active? (constantly true)
                  http/post (fn [_ _] {:status 200 :body (json/generate-string {:result 42})})]
      (go
        (let [results (<! (client/parallel-map 'add [1 2 3 4 5]))]
          (is (= 5 (count results))) ;; Проверяем, что мы получили 5 результатов
          (is (every? #(= 42 %) results)))))))

;; тест на проверку правильности распределения задач по серверам, при условии, что серверов < задач
;; фокусируется на распределении задач и проверяет, что каждая задача была выполнена на сервере

(deftest test-job-distribution
  (let [response-chan (chan)
        servers [{:id 1 :port 3000 :load 0}
                 {:id 2 :port 3001 :load 0}
                 {:id 3 :port 3002 :load 0}]]
    (with-redefs [client/get-available-servers (constantly servers)
                  client/is-server-active? (constantly true)
                  http/post (fn [url {:keys [body]}]
                              (let [request (json/parse-string body true)
                                    server-id (-> url (re-find #"(\d+)") second)]
                                   ;; Эмулируем ответ сервера на основе его ID
                                (if (= server-id "3000")
                                  {:status 200 :body (json/generate-string {:result 42 :server-id 1})}
                                  {:status 200 :body (json/generate-string {:result 42 :server-id (Integer. server-id)})})))
                  client/choose-server (fn [_]
                                         (let [active-servers (filter client/is-server-active? (client/get-available-servers))]
                                           (if (empty? active-servers)
                                             (throw (Exception. "No available servers."))
                                             (first active-servers))))]

        ;; Запускаем параллельную задачу
      (go
        (let [results (<! (client/parallel-map 'add [1 2 3 4]))]
            ;; Проверяем, что у нас 4 результата
          (is (= 4 (count results)))

            ;; Проверяем, что каждая задача была выполнена на сервере
          (let [server-ids (map :server-id results)]
            (is (= (set server-ids) (set (map :id servers)))) ;; Проверяем, что все серверы получили по задаче
            (is (= (last server-ids) 1)) ;; Проверяем, что последняя задача была выполнена на первом сервере
            ))))))

;; Запускаем тесты
(run-tests)


