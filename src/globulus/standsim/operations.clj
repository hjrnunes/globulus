(ns globulus.standsim.operations
  (:require [charred.api :as csv]
            [clojure.java.io :as io]
            [tablecloth.api :as tc]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [tech.v3.dataset :as ds]
            [clojure.pprint :as pprint]))

(defn round
  ([v]
   (round v 2))
  ([v decimals]
   (if (zero? (int v))                                      ; ArithmeticException when denominator of rem is 0
     (pprint/cl-format nil (str "~," decimals "f") v)
     (if (some zero? [decimals (rem v (int v))])
       (str (int v))
       (pprint/cl-format nil (str "~," decimals "f") v)))))

(defn gen-types
  "Generate code -> type mapping"
  []
  (let [file (-> (io/resource "simdata/FMA41_Ec_Regular.csv")
                 (str))
        data (-> (slurp file)
                 (csv/read-csv))
        [_r0 _r1 r2 r3 & _rows] data
        type (->> (drop 13 r2)
                  (mapv parse-long))
        code (drop 13 r3)]
    (zipmap code type)))

(defn fix-ops []
  (let [types (-> (io/resource "simdata/op_types.edn")
                  (str)
                  (slurp)
                  (edn/read-string))
        dataset (-> (io/resource "simdata/ops_costs.csv")
                    (str)
                    (tc/dataset)
                    (tc/rename-columns {"\uFEFFFONTE" "FONTE"})
                    (tc/map-columns "CODE" ["CODE"] (fn [code]
                                                      (-> code
                                                          (str/replace "OPMAN_" "1_")
                                                          (str/replace "OPMIS_" "2_")
                                                          (str/replace "OPMEC_" "3_"))))
                    (tc/map-columns "TYPE" ["CODE"] types)
                    )
        ]
    (ds/write! dataset "/Users/hjrnunes/workspace/hjrnunes/globulus/resources/simdata/op_costs_fixed.csv")))


(defn opmanual-prices [{:strs [FONTE] :as row}]
  (let [per-day (get row "Labour_€/day")]
    (cond-> row
            (and (pos? per-day) (not= "introduzido" FONTE))
            (merge (let [min-wu-day (get row "min units/days")
                         max-wu-day (get row "max units/days")
                         lab-div (get row "labour div")
                         med-wu-day (double (/ (+ min-wu-day max-wu-day) 2))
                         f (comp round (case lab-div "day/unit" * /))]
                     {"med units/days" med-wu-day
                      "Med"            (f per-day med-wu-day)
                      "Min"            (f per-day min-wu-day)
                      "Max"            (f per-day max-wu-day)})))))

(defn opmec-prices [{:strs [FONTE] :as row}]
  (let [per-h (get row "OPMEC_€/h")]
    (cond-> row
            (and (pos? per-h) (not= "introduzido" FONTE))
            (merge (let [min-wu-day (get row "min units/days")
                         max-wu-day (get row "max units/days")
                         med-wu-day (double (/ (+ min-wu-day max-wu-day) 2))
                         f (comp round *)]
                     {"med units/days" med-wu-day
                      "Med"            (f per-h med-wu-day)
                      "Min"            (f min-wu-day per-h)
                      "Max"            (f max-wu-day per-h)
                      })))))

(defn op-prices
  "Calculate op prices from units and time unit price"
  []
  (let [dataset (-> (io/resource "simdata/op_costs.csv")
                    (str)
                    (tc/dataset)
                    (ds/row-map (fn [{:strs [TIPO] :as row}]
                                  (case TIPO
                                    "OPMANUAL" (opmanual-prices row)
                                    "OPMISTA" (opmanual-prices row)
                                    "OPMECANICA" (opmec-prices row)
                                    "INFRAESTRUT" (opmec-prices row))))
                    (tc/reorder-columns ["Min"
                                         "Med"
                                         "Max"
                                         "min units/days"
                                         "med units/days"
                                         "max units/days"]))]
    (-> dataset
        (ds/write! "/Users/hjrnunes/workspace/hjrnunes/globulus/resources/simdata/op_costs.csv"))))

(defn ->op-row [{:strs [Unit TYPE] :as row}]
  (let [unit-jorna (get row "med units/days")
        amount (get row "Med")
        energy-l-h (some-> (get row "OPMEC_hp")
                           (/ 10))
        labour-male (-> (get row "Labour_Male")
                        (case 1 true false))
        labour-expert (-> (get row "Labour_Expert")
                          (case 1 true false))]
    (-> {"TYPE"           TYPE
         "OPERATIONS"     (get row "EN")
         "unit_jorna"     (or unit-jorna 0.0)
         "WAGExp_M"       (if (and labour-male labour-expert) 1 0)
         "WAGExp_F"       (if (and (not labour-male) labour-expert) 1 0)
         "WAGNExp_M"      (if (and labour-male (not labour-expert)) 1 0)
         "WAGNExp_F"      (if (and (not labour-male) (not labour-expert)) 1 0)
         "OP_eur_tree"    0
         "OP_eur_km"      0
         "OP_eur_m3"      0
         "OP_eur_ha"      0
         "MA_labour_h_km" 0
         "MA_labour_h_ha" 0
         "MA_energy_l_h"  0
         "OtherCosts"     0}
        (merge (case Unit
                 "€/un" {"OP_eur_tree" amount}

                 "€/ha" {"OP_eur_ha"      amount
                         "MA_labour_h_ha" unit-jorna
                         "MA_energy_l_h"  energy-l-h}

                 "€/km" {"OP_eur_km"      amount
                         "MA_labour_h_km" unit-jorna
                         "MA_energy_l_h"  energy-l-h}

                 "€/m3" {"OP_eur_m3" amount}
                 {}
                 )))))

(defn generate [{:keys [rate
                        additional-rates
                        maintenance-costs
                        file]}]
  (let [header ["TYPE"
                "OPERATIONS"
                "unit_jorna"
                "OP_eur_tree"
                "OP_eur_km"
                "OP_eur_m3"
                "OP_eur_ha"
                "MA_labour_h_km"
                "MA_labour_h_ha"
                "MA_energy_l_h"
                "WAGExp_M"
                "WAGExp_F"
                "WAGNExp_M"
                "WAGNExp_F"
                "OtherCosts"]
        ops_rows (-> (io/resource "simdata/costs_operations.csv")
                     (str)
                     (tc/dataset)
                     (tc/select-rows (comp some? #(get % "TYPE")))
                     (ds/row-map ->op-row)
                     (tc/order-by ["TYPE"])
                     (tc/select-columns header)
                     (tc/rows :as-seqs))
        n-ops (count ops_rows)

        pad-row (fn [row]
                  (let [padding (- 15 (count row))]
                    (concat row (repeat padding ""))))

        rates-row (-> (concat ["ActualRate" rate (count additional-rates)] additional-rates) (pad-row))
        costs-maint-row (-> ["CostsMaint" maintenance-costs] (pad-row))
        nops-row (-> ["Noperations" n-ops] (pad-row))
        csvdata (concat [rates-row
                         costs-maint-row
                         nops-row
                         header]
                        ops_rows)]
    (if file
      (let [writer (io/writer file :encoding "windows-1252")]
        (csv/write-csv writer
                       csvdata
                       :newline :cr+lf
                       :quote \'
                       :quote? (fn [s] (and (not (str/blank? s))
                                            (not (some? (parse-double s)))))))
      csvdata)))

(comment
  (generate {:rate              4
             :additional-rates  [3 4 5]
             :maintenance-costs 5
             :file              "/Users/hjrnunes/workspace/hjrnunes/globulus/resources/simdata/gen_ops.csv"}))
