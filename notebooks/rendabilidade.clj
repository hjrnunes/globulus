(ns rendabilidade
  {:nextjournal.clerk/visibility {:code :fold}}
  (:require [nextjournal.clerk :as clerk]
            [tablecloth.api :as tc]
            [clojure.java.io :as io]
            [globulus.globulus :as g]
            [babashka.fs :as fs]
            [clojure.edn :as edn]))

;; # Estudo de rendibilidade
;; Estudo dos indicadores de rendibilidade para uma plantação de *E. Globolus*.

;; ## Introdução

;; As plantações de *E. Globolus* caracterizam-se por duas fases: a plantação propriamente dita, quer de pés de árvore em covas ou por sementeira;
;; e a *talhadia*, que consiste em aproveitar e seleccionar os rebentos que brotam das toiças (cepos) dos eucaliptos abatidos (é uma das grandes vantagens dos eucaliptos).
;;
;; A cada fase de plantação/talhadia até ao corte chama-se rotação. Há sempre uma primeira rotação de plantio e zero, uma ou várias de talhadia.
;; Pelo que tenho visto, o mais comum é uma, ou duas no máximo, de talhadia. Duas ou três rotações, portanto, de 10 anos cada.
;;
;; ### Modelo
;;
;; A fonte principal dos dados é um [simulador/modelo empírico](https://www.isa.ulisboa.pt/cef/forchange/fctools/pt/PlataformasIMfLOR_/Povoamento/GLOBULUS)
;; de crescimento do *E. Globulus* feito especificamente para Portugal.
;; Daquilo que vi, é bastante fiável. Saber se os parâmetros que lá meti fazem sentido já é outra coisa. Há um manual de instruções manhoso,
;; que mal diz como se põe a correr. Seja como for, consegui, mas procurei não fugir muito aos exemplos.
;;
;; O modelo leva como *input*:
;; - o horizonte de planeamento em anos
;; - uma prescrição de operações, e o momento em que se executam, *e.g.* plantação, fertilização, limpeza do mato, etc.
;; - uma matriz de custos das operações
;; - uma caracterização da plantação *e.g.* densidade, etc.
;; - dados climatéricos do local *e.g.* temperatura média, precipitação, etc.
;; - índice de local, basicamente a altura máxima média atingida pelos eucaliptos naquele sítio
;; - prescrição das rotações
;; - preços de mercado da biomassa produzida
;; - taxa de desconto para o cálculo da rendibilidade

;; Como *output* produz o que se chama uma tabela de produção (yield table), que indica os parâmetros simulados para cada ano durante
;; o horizonte de planeamento; os principais são os de biomassa, claro, mas vêm também custos da produção, receitas, Net Present Value, etc.

;; Portanto, o que eu fiz foi escolher um sítio, tomando como exemplo aquele terreno em Alhadas que meti no canal, arranjei os dados climatéricos
;; mais próximos e específicos que consegui, e gerei combinações dos factores-chave que podem variar e corri o modelo para cada combinação.
;; Ao *output* do modelo acrescentei apenas o cálculo do Land Expectation Value que é o indicador clássico para projectos florestais.

;; ### Factores-chave

;; - **densidade**: quantas árvores por héctar; para Portugal, é sempre entre 1000 e 1600 pés, para zonas de mau e bom rendimento, respectivamente
;; - **factor de talhadia (coppice)**: quantos rebentos se deixa crescer por toiça cortada, para a talhadia; entre 1 e 3
;; - **taxa de desconto**: taxa abaixo da qual o investimento não é compensatório (por, por exemplo, se poder obter uma taxa melhor em depósitos a prazo, acções, etc.)
;; - **preço da madeira polpável**: a quanto contamos vender a mercadoria, em euros por metro cúbico; o valor mais actual que encontrei foi €30

;; ### Dados gerais

(clerk/table {:rows (-> [["Local" "Alhadas, Figueira da Foz"]
                         ["Horizonte de planeamento" "20 anos (1 rotação de plantio + 1 rotação de talhadia)"]
                         ["Custo recorrente anual (e.g. de manutenção, etc.)" "€30"]])})


^{:nextjournal.clerk/visibility {:result :hide}}
(def titles {"Ndead"      "Number of dead trees per hectare (ha-1)"
             "N_ing"      "Number of ingrowth trees per hectare (ha-1)"
             "Fw"         "Wilson factor – relative stand density measure (varies from 0.2 up to 0.4)"
             "G"          "Stand basal area (m2 ha-1)"
             "dg"         "Quadratic mean diameter at breast height – corresponds to the dimeter of the average tree in the stand(cm)"
             "Vu"         "Standing volume under-bark including stump (m3 ha-1)"
             "Vb"         "Volume of the bark including stump (m3 ha-1)"
             "Vst"        "Volume of the stump (m3 ha-1)"
             "V"          "Standing volume over-bark including stump (m3 ha-1)"
             "V_as1"      "Harvested volume of assortment 1 (m3 ha-1)"
             "V_as2"      "Harvested volume of assortment 2 (m3 ha-1)"
             "V_as3"      "Harvested volume of assortment 3 (m3 ha-1)"
             "V_as4"      "Harvested volume of assortment 4 (m3 ha-1)"
             "V_as5"      "Harvested volume of assortment 5 (m3 ha-1)"
             "Vharv"      "Total harvested volume sum of all assortments (thinning + final harvest)"
             "Vtot"       "?"
             "maiV"       "volume mean annual increment (m3 ha-1)"
             "iV"         "volume current annual increment (m3 ha-1)"
             "Ww"         "Stand wood biomass (Mg ha-1)"
             "Wl"         "Stand leaves biomass (Mg ha-1)"
             "Wb"         "Stand bark biomass (Mg ha-1)"
             "Wbr"        "Stand branches biomass (Mg ha-1)"
             "Wa"         "Stand aboveground biomass (Mg ha-1)"
             "Wr"         "Stand root biomass (Mg ha-1)"
             "WPmnuts"    "Stand pine-nuts biomass - stone pine (Mg ha-1)"
             "Wtop"       "Stand residues biomass - top biomass (Mg ha-1)"
             "Wtopbr"     "Stand residues biomass - top and branches biomass (Mg ha-1)"
             "Wb_bic"     "Stand residues biomass - bark (Mg ha-1)"
             "Wbrl"       "Stand residues biomass - leaves and branches (Mg ha-1)"
             "C_seq_prod" "Carbon sequestered in the products (Mg ha-1)"
             "PC_tot"     "Total production costs from inside and outside the forestry wood chain and labour costs (seedling, seeds) (€)"
             "PC_in"      "Production costs from inside the forestry wood chain (seedling, seeds) (€)"
             "PC_out"     "Production costs from outside the forestry wood chain (fertilizers, fences,...) (€)"
             "PC_lab"     "Production costs labour (€)"
             "R_wood"     "Wood revenues (€)"
             "R_biom"     "Biomass revenues (€)"
             "R_Pmnuts"   "Pinenuts revenues (€)"
             "NPV"        "Net present value (€)"
             "NPVsum"     "Sum of the net present value (€)"
             "EEA"        "? (€)"})

^{:nextjournal.clerk/visibility {:result :hide}}
(def runs-dir "/Users/hjrnunes/workspace/hjrnunes/globulus/tmp/runs")

^{:nextjournal.clerk/visibility {:result :hide}}
(def runs (->> (fs/list-dir runs-dir)
               (mapv (comp str (partial fs/relativize runs-dir)))
               (remove #{".DS_Store"})))

^{:nextjournal.clerk/visibility {:result :hide}}
(defn load-run [runs-dir run]
  (let [run-dir (fs/path runs-dir run)
        params-file (fs/path run-dir "params.edn")
        yt-file (fs/path run-dir "output" "output_YieldTable.csv")]
    {:run         run
     :params      (-> params-file
                      (str)
                      (slurp)
                      (edn/read-string))
     :yield-table (-> yt-file
                      (str)
                      (tc/dataset))}))

^{:nextjournal.clerk/visibility {:result :hide}}
(def run-yields (->> runs
                     (mapv (partial load-run runs-dir))
                     (mapv (fn [{:keys [params yield-table]}]
                             (let [{:keys [rate site plan-years pulpwood-price density coppice-factor]} params]
                               (-> yield-table
                                   (tc/add-column :t (concat [0] (range 0 (tc/row-count yield-table))))
                                   (tc/add-column :rate rate)
                                   (tc/add-column :pulpwood-price pulpwood-price)
                                   (tc/add-column :coppice-factor coppice-factor)
                                   (tc/add-column :density density)
                                   (tc/add-column :site (name site))
                                   (g/land-expectation-value plan-years (/ rate 100) {:cost-col    "PC_tot"
                                                                                      :revenue-col "R_wood"
                                                                                      :t-col       :t})))))))
^{:nextjournal.clerk/visibility {:result :hide}}
(def yield-table-ds (->> run-yields
                         (apply tc/concat)))

^{:nextjournal.clerk/visibility {:result :hide}}
(def summary-ds (->> run-yields
                     (mapv (fn [run-ds]
                             (let [[cost-in cost-out cost-lab rev-wood]
                                   (-> (tc/aggregate-columns run-ds ["PC_in", "PC_out", "PC_lab" "R_wood"] #(reduce + %))
                                       (tc/rows :as-seqs)
                                       (first))
                                   net-cost (+ cost-in cost-out cost-lab)
                                   net-flow (- rev-wood net-cost)]
                               (-> (tc/last run-ds)
                                   (tc/drop-columns ["PC_in", "PC_out", "PC_lab" "R_wood"])
                                   (tc/add-column "Cost IN" cost-in)
                                   (tc/add-column "Cost EX" cost-out)
                                   (tc/add-column "Cost - labour" cost-lab)
                                   (tc/add-column "Cost (€ ha-1)" net-cost)
                                   (tc/add-column "Revenue" rev-wood)
                                   (tc/add-column "Net cashflow (€ ha-1)" net-flow)
                                   (tc/rename-columns {:land-expectation-value "Land Expectation Value (€ ha-1)"
                                                       "NPVsum"                "Net Present Value (€ ha-1)"
                                                       :density                "Density (trees ha-1)"
                                                       :rate                   "Discount rate (%)"
                                                       :pulpwood-price         "Pulpwood price (€ m3-1)"
                                                       :coppice-factor         "Coppice factor"})
                                   (tc/select-columns ["Cost IN"
                                                       "Cost EX"
                                                       "Cost - labour"
                                                       "Cost (€ ha-1)"
                                                       "Revenue"
                                                       "Net cashflow (€ ha-1)"
                                                       "Land Expectation Value (€ ha-1)"
                                                       "Net Present Value (€ ha-1)"
                                                       "Density (trees ha-1)"
                                                       "Discount rate (%)"
                                                       "Pulpwood price (€ m3-1)"
                                                       "Coppice factor"])))))
                     (apply tc/concat)))


^{:nextjournal.clerk/visibility {:result :hide}}
(def summary (-> summary-ds
                 (tc/rows :as-maps)
                 (vec)))



;; ## Taxa de desconto vs. Preço da madeira
;; Os preços da madeira usados aqui correspondem ao preço da madeira de pé, isto é, antes de ser colhida ou abatida, e com a casca.

;; Este preço é o mais baixo que se obtém, visto que a qualidade da madeira apenas pode melhorar, sendo a partir daí limpa e escolhida.

;; Suspeito que o cálculo do simulador usa o volume de madeira já escolhida e limpa, ou seja, mais cara. Portanto, isto deverão ser estimativas por baixo.

;; Obviamente, quanto mais baixa a taxa de desconto e melhor o preço da madeira, melhores resultados se obtêm. Mas é uma perspectiva sobre as possíveis variações de um e outro.

^{::clerk/width :wide}
(clerk/vl
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :data    {:values summary}
   :config  {:axis {:grid true :tickBand "extent"}}
   :columns 2
   :resolve {:scale {:color "independent"}}
   :concat  [{:width    150
              :height   150
              :mark     {:type    "rect"
                         :tooltip {:signal "datum"}}
              :encoding {:y     {:field "Discount rate (%)"
                                 :type  :nominal}
                         :x     {:field "Pulpwood price (€ m3-1)"
                                 :type  :nominal}
                         :color {:field     "Land Expectation Value (€ ha-1)"
                                 :aggregate "max"
                                 :type      :quantitative
                                 :scale     {:scheme "yellowgreen"}}}}
             {:width    150
              :height   150
              :mark     {:type    "rect"
                         :tooltip {:signal "datum"}}
              :encoding {:y     {:field "Discount rate (%)"
                                 :type  :nominal}
                         :x     {:field "Pulpwood price (€ m3-1)"
                                 :type  :nominal}
                         :color {:field     "Net Present Value (€ ha-1)"
                                 :aggregate "max"
                                 :type      :quantitative
                                 :scale     {:scheme "yellowgreen"}}}}
             {:width    150
              :height   150
              :mark     {:type    "rect"
                         :tooltip {:signal "datum"}}
              :encoding {:y     {:field "Discount rate (%)"
                                 :type  :nominal}
                         :x     {:field "Pulpwood price (€ m3-1)"
                                 :type  :nominal}
                         :color {:field     "Net cashflow (€ ha-1)"
                                 :aggregate "max"
                                 :type      :quantitative
                                 :scale     {:scheme "yellowgreen"}}
                         }}
             ]
   })



;; ## Factores da plantação vs. factores económicos

;; Os dois factores de platanção mais importantes são:

;; - *Density*: Densidade das árvores, expressa em árvores por hectar
;; - *Coppice factor*: Número de varas que se deixa crescer em cada toiça (cepo) para a 2.ª rotação

;; Contava com uma relação menos linear quanto à densidade. Maior densidade resulta em menos diâmetro basal (do tronco).
;; Logo, poderia significar menos volume a partir de certo ponto. Não sei se é o simulador que não leva isso em conta ou se é mesmo assim.

;; Seja como for, é pelo menos útil para ter uma perspectiva para vários tipos de localização (solo, clima, etc):

;; - de bom rendimento: densidade de 1300 a 1600, coppice de 1,5 a 3
;; - de menor rendimento: densidade de 1000 a 1300, coppice de 1 a 1,5

;; Neste local julgo que seria possível ir até aos parâmetros máximos. Mas noutros locais, se forem muito acidentados, por exemplo,
;; pode não ser.

^{::clerk/width :wide}
(clerk/vl
  {:$schema   "https://vega.github.io/schema/vega-lite/v5.json"
   :data      {:values summary}
   :params    [{:name  "selected_rate"
                :value 3
                :bind  {:input "range" :min 3 :max 6 :step 1}}
               {:name  "selected_price"
                :value 29
                :bind  {:input "range" :min 28 :max 31 :step 1}}]
   :transform [{:filter "datum['Pulpwood price (€ m3-1)'] == selected_price"}
               {:filter "datum['Discount rate (%)'] == selected_rate"}]

   :config    {:axis {:grid true :tickBand "extent"}}
   :columns   2
   :resolve   {:scale {:color "independent"}}
   :concat    [{:width    150
                :height   150
                :mark     {:type    "rect"
                           :tooltip {:signal "datum"}}
                :encoding {:y     {:field "Coppice factor"
                                   :type  :nominal}
                           :x     {:field "Density (trees ha-1)"
                                   :type  :nominal}
                           :color {:field "Land Expectation Value (€ ha-1)"
                                   :type  :quantitative
                                   :scale {:scheme "yellowgreen"}}}}
               {:width    150
                :height   150
                :mark     {:type    "rect"
                           :tooltip {:signal "datum"}}
                :encoding {:y     {:field "Coppice factor"
                                   :type  :nominal}
                           :x     {:field "Density (trees ha-1)"
                                   :type  :nominal}
                           :color {:field "Net Present Value (€ ha-1)"
                                   :type  :quantitative
                                   :scale {:scheme "yellowgreen"}}}}
               {:width    150
                :height   150
                :mark     {:type    "rect"
                           :tooltip {:signal "datum"}}
                :encoding {:y     {:field "Coppice factor"
                                   :type  :nominal}
                           :x     {:field "Density (trees ha-1)"
                                   :type  :nominal}
                           :color {:field "Net cashflow (€ ha-1)"
                                   :type  :quantitative
                                   :scale {:scheme "yellowgreen"}}
                           }}

               {:width    150
                :height   150
                :mark     {:type    "rect"
                           :tooltip {:signal "datum"}}
                :encoding {:y     {:field "Coppice factor"
                                   :type  :nominal}
                           :x     {:field "Density (trees ha-1)"
                                   :type  :nominal}
                           :color {:field "Cost (€ ha-1)"
                                   :type  :quantitative
                                   :scale {:scheme "yelloworangered"}}
                           }}
               ]
   })



;; ## Caso específico
(clerk/table {:rows (-> [["taxa" 4]
                         ["densidade" 1250]
                         ["factor talhadia" 1.5]
                         ["preço da madeira" 30]])})

^{:nextjournal.clerk/visibility {:result :hide}}
(def single-case-ds (-> yield-table-ds
                        (tc/select-rows (fn [row]
                                          (and (= 4 (:rate row))
                                               (= 1250 (:density row))
                                               (= 1.5 (:coppice-factor row))
                                               (= 30 (:pulpwood-price row)))))))

^{:nextjournal.clerk/visibility {:result :hide}}
(def single-case-rows (tc/rows single-case-ds :as-maps))

;; ### Despesas por ano
;; - *Dentro da cadeia de valor florestal* respeita a despesas com: sementes, pés de árvore, etc.
;; - *Fora da cadeira de valor florestal* respeita a despesas com: fertilizantes, vedações, etc.

^{::clerk/width :wide}
(clerk/vl
  {:$schema   "https://vega.github.io/schema/vega-lite/v5.json"
   :width     400
   :height    500
   :title     "Despesa por tipo (€ / ha)"
   :data      {:values single-case-rows}
   :transform [{:fold ["PC_in", "PC_out", "PC_lab"]
                :as   ["cost_type" "cost"]}]
   :mark      {:type    "bar"
               :tooltip true}
   :encoding  {:x     {:field "year"
                       :title "Ano"}
               :y     {:aggregate "sum"
                       :field     "cost"
                       :title     "Despesa"}
               :color {:field  "cost_type"
                       :legend {:title     "Tipo de despesa"
                                :labelExpr "datum.label == 'PC_in' ? 'Dentro da cadeia de valor florestal' : datum.label == 'PC_out' ? 'Fora da cadeia de valor florestal' :datum.label == 'PC_lab' ? 'Mão de obra' : 'Video Game'"}}}})


;; ### Cash flow

^{::clerk/width :wide}
(clerk/vl
  {:$schema   "https://vega.github.io/schema/vega-lite/v5.json"
   :width     200
   :height    200
   :title     "Cash flow (€ ha-1)"
   :data      {:values single-case-rows}
   :transform [{:aggregate [{:op "sum" :field "PC_tot" :as "Despesa"}
                            {:op "sum" :field "R_wood" :as "Receita"}]}
               {:fold ["Despesa", "Receita"]
                :as   ["flow_type" "amount"]}]
   :mark      {:type    "bar"
               :tooltip true}
   :encoding  {:x     {:field "flow_type"
                       :type  "nominal"
                       :title nil
                       :axis  {:labelAngle   0
                               :labelPadding 10
                               :ticks        false}}
               :y     {:field "amount"
                       :type  "quantitative"}
               :color {:field  "flow_type"
                       :legend nil}}})

;; ### LEV & NPV

^{::clerk/width :wide}
(clerk/vl
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :data    {:values single-case-rows}
   :columns 2
   :concat  [{:title    "Land Expectation Value - accumulated"
              :mark     {:type "bar" :tooltip true}
              :width    300
              :encoding {:x {:field "year"
                             :title "Ano"}
                         :y {:aggregate "max"
                             :field     :land-expectation-value
                             :type      "quantitative"
                             :title     nil}}}

             {:title    "Land Expectation Value - year net"
              :mark     {:type "bar" :tooltip true}
              :width    300
              :encoding {:x {:field "year"
                             :title "Ano"}
                         :y {:aggregate "max"
                             :field     :lv-net
                             :type      "quantitative"
                             :title     nil}}}

             {:title    "Net Present Value - accumulated"
              :mark     {:type "bar" :tooltip true}
              :width    300
              :encoding {:x {:field "year"
                             :title "Ano"}
                         :y {:aggregate "max"
                             :field     "NPVsum"
                             :type      "quantitative"
                             :title     nil}}}

             {:title    "Net Present Value - year net"
              :mark     {:type "bar" :tooltip true}
              :width    300
              :encoding {:x {:field "year"
                             :title "Ano"}
                         :y {:aggregate "max"
                             :field     "NPV"
                             :type      "quantitative"
                             :title     nil}}}
             ]})


;; ## Dados em bruto


(clerk/md (str "Simulações: " (tc/row-count summary)))

^{::clerk/width :wide}
(clerk/table yield-table-ds)