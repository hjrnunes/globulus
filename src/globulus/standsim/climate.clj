(ns globulus.standsim.climate
  (:require [charred.api :as csv]
            [clojure.java.io :as io]))

(def sites {:coruche {:id_met    144
                      :daysRain  72.4
                      :rain      641.7
                      :Temp      15.96667
                      :daysFrost 18.3
                      :Prec3     641.7
                      :Prec4     641.7
                      :X         112960.9
                      :Y         200555.9}
            :coimbra {:id_met    549
                      :daysRain  103.6
                      :rain      1015.8
                      :Temp      15.725
                      :daysFrost 11
                      :Prec3     1015.8
                      :Prec4     1015.8
                      :X         112960.9
                      :Y         200555.9}
            :alhadas {:id_met    763
                      :daysRain  111.4
                      :rain      1044.0
                      :Temp      15.83
                      :daysFrost 6
                      :Prec3     1044.0
                      :Prec4     1044.0
                      :X         -8.7826
                      :Y         40.1886}})

(defn generate [{:keys [file
                        id_met
                        daysRain
                        rain
                        Temp
                        daysFrost
                        Prec3
                        Prec4
                        X
                        Y]}]
  (let [header ["id_met"
                "daysRain"
                "rain"
                "Temp"
                "daysFrost"
                "Prec3"
                "Prec4"
                "X"
                "Y"]
        csvdata [header
                 [id_met
                  daysRain
                  rain
                  Temp
                  daysFrost
                  Prec3
                  Prec4
                  X
                  Y]]]
    (if file
      (let [writer (io/writer file :encoding "windows-1252")]
        (csv/write-csv writer
                       csvdata
                       :newline :cr+lf))
      csvdata)))