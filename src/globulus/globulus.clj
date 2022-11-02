(ns globulus.globulus
  (:require [tablecloth.api :as tc]
            [tech.v3.dataset :as ds]
            [tech.v3.datatype.functional :as dfn])
  (:gen-class))

(defn net-present-value-map [rate {:keys [cost-col revenue-col t-col]} row]
  (let [cost (get row cost-col)
        revenue (get row revenue-col)
        year (get row t-col)
        compound (Math/pow (+ rate 1) year)]
    {:pv-cost    (/ cost compound)
     :pv-revenue (/ revenue compound)
     :pv-net     (/ (- revenue cost) compound)}))

(defn net-present-value
  ([dataset rate]
   (net-present-value dataset rate {:cost-col :cost :revenue-col :revenue :t-col :year}))
  ([dataset rate cols]
   (let [presents (ds/row-map dataset (partial net-present-value-map rate cols))
         pv (dfn/cumsum (:pv-net presents))]
     (assoc presents :present-value pv))))


(defn compound-formula [rate
                        total-years
                        current-year]
  (let [years-left (- total-years current-year)]
    (Math/pow
      (+ rate 1)
      years-left)))


(defn land-expectation-value-map [years-per-rotation rate {:keys [cost-col revenue-col t-col]} row]
  (let [cost (get row cost-col)
        revenue (get row revenue-col)
        year (get row t-col)
        compound (compound-formula rate years-per-rotation year)]
    {:lv-cost    (/ cost compound)
     :lv-revenue (/ revenue compound)
     :lv-net     (* (- revenue cost) compound)}))

(defn land-expectation-value
  ([dataset
    years-per-rotation
    rate]
   (land-expectation-value dataset years-per-rotation rate {:cost-col :cost :revenue-col :revenue :t-col :year}))
  ([dataset
    years-per-rotation
    rate
    cols]
   (let [presents (ds/row-map dataset (partial land-expectation-value-map years-per-rotation rate cols))
         pv (dfn/cumsum (:lv-net presents))]
     (assoc presents :land-expectation-value pv))))


(comment
  (let [data (concat [{:year 18 :revenue 96 :cost 0}
                      {:year 25 :revenue 160 :cost 0}
                      {:year 30 :revenue 912 :cost 0}
                      {:year 0 :revenue 0 :cost 40}
                      {:year 0 :revenue 0 :cost 40}]
                     (->> (range 1 31)
                          (map (fn [y] {:year y :revenue 0 :cost 1.5}))))
        dataset (-> (tc/dataset data)
                    (tc/order-by :year))]
    (-> dataset
        (land-expectation-value 30 0.04)))
  )

(def operations-ref
  [{:description "Sacha e Amontoa"
    :amount      -75}
   {:description "Adubação Manual"
    :amount      -140}
   {:description "Adubação Mecanizada"
    :amount      -120}
   {:description "Controlo Vegetação - Corta matos mecanizado"
    :amount      -100}
   {:description "Controlo Vegetação - Gradagem"
    :amount      -75}
   {:description "Controlo Vegetação - Motoroçadora (na linha ou terraços)"
    :amount      -350}
   {:description "Controlo Vegetação - Herbicida"
    :amount      -160}
   {:description "Selecção de Varas (1a)"
    :amount      -75}
   {:description "Selecção de Varas (2a)"
    :amount      -47}
   {:description "Caminhos e aceiros - Conservação"
    :amount      -10}
   {:description "Pragas/Doenças (Gonipterus ou Phorocantha)"
    :amount      -52}
   {:description "Instalação"
    :amount      -1400}])



(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (greet {:name (first args)}))
