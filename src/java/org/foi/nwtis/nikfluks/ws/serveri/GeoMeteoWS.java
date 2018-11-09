package org.foi.nwtis.nikfluks.ws.serveri;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.jws.WebService;
import javax.jws.WebMethod;
import org.foi.nwtis.nikfluks.konfiguracije.bp.BP_Konfiguracija;
import org.foi.nwtis.nikfluks.rest.klijenti.GMKlijent;
import org.foi.nwtis.nikfluks.rest.klijenti.OWMKlijent;
import org.foi.nwtis.nikfluks.web.podaci.Lokacija;
import org.foi.nwtis.nikfluks.web.podaci.MeteoPodaci;
import org.foi.nwtis.nikfluks.web.podaci.Parkiraliste;
import org.foi.nwtis.nikfluks.web.slusaci.SlusacAplikacije;

/**
 * Klasa je server za soap servise, tj. ona prima, obrađuje soap zahtjeve i vraća odgovor
 *
 * @author Nikola
 * @version 1
 */
@WebService(serviceName = "GeoMeteoWS")
public class GeoMeteoWS {

    String url;
    String korIme;
    String lozinka;
    String uprProgram;
    Connection con;
    String gmapikey;
    String apikey;
    OWMKlijent owmk;
    GMKlijent gmk;

    /**
     * Metoda služi za dohvat podataka iz kofiguracijske datoteke
     *
     * @return true ako su uspješno dohvaćeni podaci, inače false
     */
    private boolean dohvatiPodatkeIzKonfiguracije() {
        try {
            BP_Konfiguracija bpk = (BP_Konfiguracija) SlusacAplikacije.getServletContext().getAttribute("BP_Konfig");
            url = bpk.getServerDatabase() + bpk.getUserDatabase();
            korIme = bpk.getUserUsername();
            lozinka = bpk.getUserPassword();
            uprProgram = bpk.getDriverDatabase();
            apikey = bpk.getApiKey();
            owmk = new OWMKlijent(apikey);
            gmapikey = bpk.getGmApiKey();
            gmk = new GMKlijent(gmapikey);
            Class.forName(uprProgram);
            return true;
        } catch (ClassNotFoundException ex) {
            System.err.println("Greška kod dohvatiPodatkeIzKonfiguracije");
            return false;
        }
    }

    /**
     * Dohvaća podatke o svim parkiralištima iz baze podataka.
     *
     * @return lista parkirališta, ili null u slučaju pogreške
     */
    @WebMethod(operationName = "dajSvaParkiralista")
    public List<Parkiraliste> dajSvaParkiralista() {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return null;
        }

        List<Parkiraliste> svaParkiralista = new ArrayList<>();
        String upit = "SELECT * FROM PARKIRALISTA";

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            ResultSet rs = stat.executeQuery();

            while (rs.next()) {
                Lokacija lokacija = new Lokacija(rs.getString("latitude"), rs.getString("longitude"));
                svaParkiralista.add(new Parkiraliste(rs.getInt("id"), rs.getString("naziv"), rs.getString("adresa"), lokacija));
            }
            rs.close();
            stat.close();
            con.close();
            return svaParkiralista;
        } catch (SQLException ex) {
            System.err.println("Greška kod dohvaćanja svih parkirališta: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Dodaje parkiralište u bazu podataka
     *
     * @param p parkiralipte koje se dodaje
     * @return true ako je uspješno dodano, false inače
     */
    @WebMethod(operationName = "dodajParkiraliste")
    public boolean dodajParkiraliste(Parkiraliste p) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return false;
        }
        Lokacija lok = null;
        try {
            lok = gmk.getGeoLocation(p.getAdresa());
        } catch (Exception e) {
            return false;
        }
        String upit = "INSERT INTO PARKIRALISTA (naziv, adresa, latitude, longitude)"
                + " VALUES ('" + p.getNaziv() + "','" + p.getAdresa() + "',"
                + lok.getLatitude() + "," + lok.getLongitude() + ")";

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            stat.execute();
            stat.close();
            con.close();
            return true;
        } catch (SQLException ex) {
            System.err.println("Greška kod upisa parkirališta: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Dohvaća sve meteo podatke o zadanom parkiralištu
     *
     * @param id id za kojeg se traže meteo podaci
     * @return lista meteo podataka, ili null u slučaju greške
     */
    @WebMethod(operationName = "dajSveMeteoPodatke")
    public List<MeteoPodaci> dajSveMeteoPodatke(int id) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return null;
        }

        String upit = "SELECT * FROM METEO WHERE id=" + id;
        List<MeteoPodaci> sviMeteoPodaci = new ArrayList<>();

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            ResultSet rs = stat.executeQuery();

            while (rs.next()) {
                MeteoPodaci mp = postaviMeteoPodatke(rs);
                sviMeteoPodaci.add(mp);
            }

            rs.close();
            stat.close();
            con.close();

            if (sviMeteoPodaci.isEmpty()) {
                return null;
            } else {
                return sviMeteoPodaci;
            }
        } catch (SQLException ex) {
            System.err.println("Greška kod dohvaćanja svih meteo podataka: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Dobivene podatake iz baze podataka sprema u objekt tipa MeteoPodaci
     *
     * @param rs podaci iz baze podataka
     * @return objekt tipa MeteoPodaci
     * @throws SQLException SQL iznimka
     */
    private MeteoPodaci postaviMeteoPodatke(ResultSet rs) throws SQLException {
        MeteoPodaci mp = new MeteoPodaci();
        mp.setWeatherNumber(rs.getInt("vrijeme"));
        mp.setWeatherValue(rs.getString("vrijemeopis"));
        mp.setTemperatureValue(rs.getFloat("temp"));
        mp.setTemperatureMin(rs.getFloat("tempmin"));
        mp.setTemperatureMax(rs.getFloat("tempmax"));
        mp.setHumidityValue(rs.getFloat("vlaga"));
        mp.setPressureValue(rs.getFloat("tlak"));
        mp.setWindSpeedValue(rs.getFloat("vjetar"));
        mp.setWindDirectionValue(rs.getFloat("vjetarsmjer"));
        mp.setLastUpdate(rs.getTimestamp("preuzeto"));
        return mp;
    }

    /**
     * Dohvaća meteo podatke o zadanom parkiralištu u zadnom vremenskom intervalu
     *
     * @param id id zadanog parkirališta
     * @param intervalOd timestamp od kojeg se traže podaci
     * @param intervalDo timestamp do kojeg se traže podaci
     * @return lista meteo podataka ili null u slučaju greške
     */
    @WebMethod(operationName = "dajSveMeteoPodatkeUIntervalu")
    public List<MeteoPodaci> dajSveMeteoPodatkeUIntervalu(int id, long intervalOd, long intervalDo) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return null;
        }

        Timestamp tsOd = new Timestamp(intervalOd);
        Timestamp tsDo = new Timestamp(intervalDo);
        List<MeteoPodaci> sviMeteoPodaci = dohvatiSveMeteoPodatkeUIntervalu(id, tsOd, tsDo);

        if (sviMeteoPodaci == null) {
            return null;
        } else {
            return sviMeteoPodaci;
        }
    }

    /**
     * Dohvaća zadnje meteo podatke o zadanom parkiralištu iz baze podataka
     *
     * @param id id zadanog parkirališšta
     * @return meteo podaci ili null u slučaju greške
     */
    @WebMethod(operationName = "dajZadnjeMeteoPodatke")
    public MeteoPodaci dajZadnjeMeteoPodatke(int id) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return null;
        }

        String upit = "SELECT * FROM METEO WHERE id=" + id + " ORDER BY PREUZETO DESC";
        List<MeteoPodaci> sviMeteoPodaci = new ArrayList<>();

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            ResultSet rs = stat.executeQuery();

            if (rs.next()) {
                MeteoPodaci mp = postaviMeteoPodatke(rs);
                sviMeteoPodaci.add(mp);
            }

            rs.close();
            stat.close();
            con.close();

            if (sviMeteoPodaci.isEmpty()) {
                return null;
            } else {
                return sviMeteoPodaci.get(0);
            }
        } catch (SQLException ex) {
            System.err.println("Greška kod dohvaćanja zadnjih meteo podataka iz baze: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Dohvaća važeće meteo podatke o zadanom parkiralištu sa web servisa
     *
     * @param id id zadanog parkirališšta
     * @return meteo podaci ili null u slučaju greške
     */
    @WebMethod(operationName = "dajVazeceMeteoPodatke")
    public MeteoPodaci dajVazeceMeteoPodatke(int id) {
        Parkiraliste p = dohvatiUnesenoParkiraliste(id);
        if (p == null) {
            return null;
        } else {
            Lokacija lok = p.getGeolokacija();
            MeteoPodaci mp = owmk.getRealTimeWeather(lok.getLatitude(), lok.getLongitude());
            if (mp == null) {
                return null;
            } else {
                return mp;
            }
        }
    }

    /**
     * Dohvaća parkiralište iz baze podataka temeljem id-a
     *
     * @param id id željenog parkirališta
     * @return parkiralište ili null u slučaju greške
     */
    private Parkiraliste dohvatiUnesenoParkiraliste(int id) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return null;
        }

        String upit = "SELECT * FROM PARKIRALISTA WHERE id=" + id;
        Parkiraliste p = null;

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            ResultSet rs = stat.executeQuery();

            if (rs.next()) {
                Lokacija lokacija = new Lokacija(rs.getString("latitude"), rs.getString("longitude"));
                p = new Parkiraliste(rs.getInt("id"), rs.getString("naziv"), rs.getString("adresa"), lokacija);
            }
            rs.close();
            stat.close();
            con.close();
            return p;
        } catch (SQLException ex) {
            System.err.println("Greška kod dohvaćanja parkirališta po IDu!\n" + ex.getMessage());
            return null;
        }
    }

    /**
     * Dohvaća minimalnu i maksimalnu temperaturu o zadanom parkiralištu u zadnom vremenskom intervalu.
     *
     * @param id id zadanog parkirališta
     * @param intervalOd timestamp od kojeg se traže podaci
     * @param intervalDo timestamp do kojeg se traže podaci
     * @return polje od 2 elementa, prvi je min temp, drugi max temp ili null u slučaju greške
     *
     */
    @WebMethod(operationName = "dajMinMaxTemp")
    public float[] dajMinMaxTemp(int id, long intervalOd, long intervalDo) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return null;
        }

        Timestamp tsOd = new Timestamp(intervalOd);
        Timestamp tsDo = new Timestamp(intervalDo);
        List<MeteoPodaci> sviMeteoPodaci = dohvatiSveMeteoPodatkeUIntervalu(id, tsOd, tsDo);
        List<Float> minTemperature = new ArrayList<>();
        List<Float> maxTemperature = new ArrayList<>();

        if (sviMeteoPodaci == null) {
            return null;
        } else {
            for (MeteoPodaci mp : sviMeteoPodaci) {
                minTemperature.add(mp.getTemperatureMin());
                maxTemperature.add(mp.getTemperatureMax());
            }
            float[] minMax = new float[2];
            minMax[0] = Collections.min(minTemperature);
            minMax[1] = Collections.max(maxTemperature);
            return minMax;
        }
    }

    /**
     * Dohvaća meteo podatke o zadanom parkiralištu u zadnom vremenskom intervalu iz baze podataka.
     *
     * @param id id zadanog parkirališta
     * @param tsOd timestamp od kojeg se traže podaci
     * @param tsDo timestamp do kojeg se traže podaci
     *
     * @return lista meteo podataka ili null u slučaju greške
     */
    private List<MeteoPodaci> dohvatiSveMeteoPodatkeUIntervalu(int id, Timestamp tsOd, Timestamp tsDo) {
        String upit = "SELECT * FROM METEO WHERE id=" + id + " AND (preuzeto BETWEEN '" + tsOd + "' AND '" + tsDo + "')";
        List<MeteoPodaci> sviMeteoPodaci = new ArrayList<>();

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            ResultSet rs = stat.executeQuery();

            while (rs.next()) {
                MeteoPodaci mp = postaviMeteoPodatke(rs);
                sviMeteoPodaci.add(mp);
            }

            rs.close();
            stat.close();
            con.close();

            if (sviMeteoPodaci.isEmpty()) {
                return null;
            } else {
                return sviMeteoPodaci;
            }
        } catch (SQLException ex) {
            System.err.println("Greška kod dohvaćanja svih meteo podataka u intervalu!\n" + ex.getMessage());
            return null;
        }
    }
}
