(ns my-project.api
  (:require [my-project.client :refer :all]
            [clojure.core.async :refer [<! >! go]] 
            [my-project.server :refer [-main]])) 

;; Запуск сервера
(-main)


;; Интерфейс для регистрации удаленной функции
(defn register-function [name code]
  (register-remote-function name code))

;; Пример использования: регистрация функции вычитания
(defn example-register-subtract []
  (register-function "subtract" "(fn [a b] (- a b))"))

(example-register-subtract) ;; Регистрация функции вычитания

;; Интерфейс для вызова удаленной функции
(defn call-remote-function [func args]
  (let [response-chan (submit-job func args nil nil)]
    (go
      (let [result (<! response-chan)]
        (when result
          (println "Result of remote function:" (:result result)))))))

;; Пример использования: вызов удаленной функции
(defn example-call-remote-function []
  (call-remote-function 'add [6 7]))

(example-call-remote-function) ;; Вызов удаленной функции

;; Интерфейс для отправки Clojure-кода
(defn submit-clojure-code [code]
  (let [response-chan (submit-job nil nil code nil)]
    (go
      (let [result (<! response-chan)]
        (when result
          (println "Result of executing Clojure code:" result))))))

;; Пример использования: отправка Clojure-кода
(defn example-submit-clojure-code []
  (submit-clojure-code "(+ 10 2)"))

(example-submit-clojure-code) ;; Отправка Clojure-кода

;; Интерфейс для отправки Java-объекта
(defn submit-java-object [java-object]
  (let [response-chan (submit-job nil nil nil java-object)]
    (go
      (let [result (<! response-chan)]
        (when result
          (println "Result of sending Java object:" result))))))

;; Пример использования: отправка Java-объекта
(defn example-submit-java-object []
  (submit-java-object {:name "Alice" :age 30}))

(example-submit-java-object) ;; Отправка Java-объекта

;; Пример использования: отправка удаленной функции вычитания
(defn example-submit-remote-function-subtract []
  (let [func 'subtract
        args [17 7]]
    (println "Submitting remote function...")
    (let [result (submit-job func args nil nil)]
      (when result
        (println "Result of remote function:" (:result result))))))

(example-submit-remote-function-subtract) ;; Пример отправки удаленной функции вычитания

;; Пример использования: параллельное применение функции
(defn example-submit-parallel-map []
  (let [func 'add
        args [1 2 3 4 5]]
    (println "Submitting parallel map...")
    (let [results (parallel-map func args)]
      (println "Results of parallel map:" results))))

(example-submit-parallel-map) ;; Параллельное применение функции




;; Пример использования: параллельное применение функции на нескольких серверах
;; Запуск трех серверов

(start-three-servers-sequentially) 

(defn example-submit-parallel-map []
  (let [func 'add
        args [1 2 3 4 5 6 7 8]] ;; Передаем отдельные числа
    (println "Submitting parallel jobs to multiple servers...")
    (let [results (parallel-map func args)]
      (println "Results of parallel jobs:" results))))

(example-submit-parallel-map) ;; Параллельное применение функции