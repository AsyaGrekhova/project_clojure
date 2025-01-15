(ns my-project.server-test
  (:require [clojure.test :refer :all]
            [my-project.server :refer :all] 
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [clj-http.client :as client]))

(defn reset-server-state []
  (reset! jobs [])
  (reset! job-status {})
  (reset! remote-functions {}))


(fact "Testing register remote function"
      (let [mock-response {:status 200
                           :body (json/generate-string {:status "Function registered"})}]

    ;; Мокируем HTTP-запрос
        (with-redefs [client/post (fn [_ _] mock-response)]

          (let [func-name "add"
                func-code "(fn [a b] (+ a b))"
                req {:body (json/generate-string {:name func-name :code func-code})
                     :headers {"Content-Type" "application/json"}}]

       
            (let [response (client/post "http://localhost:55824/register" req)]
              (fact (:status response) => 200)
              (let [body (json/parse-string (:body response) true)]
                (fact (:status body) => "Function registered")))))))

(deftest test-call-remote-function
  (reset-server-state)
  (let [func-name "add"
         func-code "(fn [a b] (+ a b))" ;; Определяем функцию в виде строки
         req {:body (java.io.ByteArrayInputStream. (.getBytes (json/generate-string {:name func-name :code func-code}))) ;; Используем ByteArrayInputStream
              :headers {"Content-Type" "application/json"}}]

    ;; Регистрация функции
    (register-function-handler req "test-server-id")

    ;; Вызов зарегистрированной функции
    (let [args [1 2]
          result (call-remote-function (symbol func-name) args)]
      (is (= 3 result)))))


(deftest test-update-job-status
  (reset-server-state)
  (let [job-id (java.util.UUID/randomUUID)]
    (update-job-status job-id "in-progress")
    (is (= {:status "in-progress"} (get @job-status job-id)))
    (update-job-status job-id "completed")
    (is (= {:status "completed"} (get @job-status job-id)))))


(deftest test-job-handler
  (reset-server-state) ;; Сброс состояния сервера
  (let [func-name "add"
        func-code "(fn [a b] (+ a b))" ;; Определяем функцию в виде строки
        req {:body (java.io.ByteArrayInputStream. (.getBytes (json/generate-string {:name func-name :code func-code}))) ;; Используем ByteArrayInputStream
             :headers {"Content-Type" "application/json"}}]

    ;; Регистрация функции
    (register-function-handler req "test-server-id")

    ;; Создание задания
    (let [job-req {:remote-func func-name :args [3 4]}
          job-request {:body (java.io.ByteArrayInputStream. (.getBytes (json/generate-string job-req))) ;; Используем ByteArrayInputStream
                       :headers {"Content-Type" "application/json"}}
          response (job-handler job-request "test-server-id")] ;; Передаем корректный req в job-handler

      (is (= 200 (:status response))) ;; Проверяем статус ответа
      (is (= (json/generate-string {:result 7}) (:body response)))))) ;; Проверяем тело ответа


(deftest test-check-function-handler
  (reset-server-state)

  ;; Определяем функцию для регистрации
  (let [func-name "add"
        func-code "(fn [a b] (+ a b))"
        req {:body (java.io.ByteArrayInputStream. (.getBytes (json/generate-string {:name func-name :code func-code})))
             :headers {"Content-Type" "application/json"}}]

    ;; Регистрация функции
    (register-function-handler req "test-server-id")
    (println "After registration, remote functions:" @remote-functions) ;; Отладка

    ;; Проверка существующей функции
    (let [check-req {:query-params {"func" "add"}} 
          response (check-function-handler check-req)]
      (println "Checking function with name:" "add") ;; Отладка
      (println "Response for existing function:" (json/parse-string (:body response) true))
      (is (= 200 (:status response)))
      (is (= {:available true} (json/parse-string (:body response) true))))

    ;; Проверка функции с nil
    (let [check-req-nil {:query-params {"func" nil}}
          response-nil (check-function-handler check-req-nil)]
      (println "Response for nil function:" (json/parse-string (:body response-nil) true))
      (is (= 200 (:status response-nil)))
      (is (= {:available false :error "Function name cannot be nil"} (json/parse-string (:body response-nil) true))))))


(run-tests)








