(ns project-smp.client-test
  (:require [clojure.test :refer :all]
            [my-project.client :as client]
            [clojure.core.async :refer [<! >! go chan]]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [cheshire.core :as json]))

(defn mock-server []
  ;; Мокируем сервер для тестирования
  (let [server (atom {})]
    (reset! server {:port 3000})
    server))

(deftest test-get-available-servers
  (testing "Get available servers"
    (with-redefs [slurp (constantly "[{:id 1 :port 3000 :load 0}]")]
      (let [servers (client/get-available-servers)]
        (is (= 1 (count servers)))
        (is (= 3000 (:port (first servers))))))))

(deftest test-choose-server
  (testing "Choose the best available server"
    (let [servers [{:id 1 :port 3000 :load 1}
                   {:id 2 :port 3001 :load 0}
                   {:id 3 :port 3002 :load 2}]]
      (with-redefs [client/get-available-servers (constantly servers)
                    client/is-server-active? (constantly true)]
        (let [server (client/choose-server 'add)]
          (is (= 3001 (:port server))))))))

(deftest test-submit-job
  (testing "Submit job to selected server"
    (let [response-chan (chan)]
      (with-redefs [http/post (fn [_ _] {:status 200 :body (json/generate-string {:result 42})})]
        (go
          (let [result (<! (client/submit-job 'add [1 2] nil nil))]
            (is (= 42 (:result result)))))))))

;; Запускаем тесты
(run-tests)
