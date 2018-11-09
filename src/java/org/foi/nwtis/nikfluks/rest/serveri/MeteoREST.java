package org.foi.nwtis.nikfluks.rest.serveri;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import org.foi.nwtis.nikfluks.konfiguracije.bp.BP_Konfiguracija;
import org.foi.nwtis.nikfluks.rest.klijenti.GMKlijent;
import org.foi.nwtis.nikfluks.rest.klijenti.OWMKlijent;
import org.foi.nwtis.nikfluks.web.podaci.Lokacija;
import org.foi.nwtis.nikfluks.web.podaci.MeteoPodaci;
import org.foi.nwtis.nikfluks.web.podaci.Parkiraliste;
import org.foi.nwtis.nikfluks.web.slusaci.SlusacAplikacije;

/**
 * Klasa je server za rest servise, tj. ona prima, obrađuje rest zahtjeve i vraća odgovor
 *
 * @author Nikola
 * @version 1
 */
@Path("meteo")
public class MeteoREST {

    String url;
    String korIme;
    String lozinka;
    String uprProgram;
    Connection con;
    String gmapikey;
    String apikey;
    OWMKlijent owmk;
    GMKlijent gmk;
    String greska = "";
    MeteoPodaci mp;

    @Context
    private UriInfo context;

    /**
     * Prazan konstruktor
     */
    public MeteoREST() {
    }

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
            greska = "Greška kod dohvaćanja podataka iz konfiguracije!";
            return false;
        }
    }

    /**
     * Dohvaća podatke o svim parkiralištima iz baze podataka i pretvara ih u json format.
     *
     * @return odgovor u json formatu
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getJson() {
        StringBuilder odg = new StringBuilder();
        try {
            List<Parkiraliste> svaParkiralista = dajSvaParkiralista();

            if (svaParkiralista != null) {
                odg.append("{\"odgovor\": [");
                int brojPark = svaParkiralista.size();
                int i = 1;

                for (Parkiraliste p : svaParkiralista) {
                    String json = new Gson().toJson(p);
                    odg.append(json);

                    if (i < brojPark) {
                        i++;
                        odg.append(", ");
                    }
                }
                odg.append("], \"status\": \"OK\"}");
            } else {
                odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"").append(greska).append("\"}");
            }
        } catch (Exception e) {
            odg = new StringBuilder();
            odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"Pogreška kod parsiranja odgovora!\"}");
        }
        greska = "";
        return odg.toString();
    }

    /**
     * Dohvaća sva parkirališta iz baze podataka.
     *
     * @return lista parkirališta ili null ako je greška
     */
    private List<Parkiraliste> dajSvaParkiralista() {
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
            greska = "Greška kod dohvaćanja svih parkirališta!";
            return null;
        }
    }

    /**
     * Dodaje parkiralište u bazu podataka.
     *
     * @param podaci naziv i adresa u json formatu
     * @return odgovor u json formatu
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public String postJson(String podaci) {
        StringBuilder odg = new StringBuilder();
        try {
            Parkiraliste p = new Gson().fromJson(podaci, Parkiraliste.class);
            boolean postoji = postojiParkiraliste(p.getId());//ako id ne postoji svejedno se unese defaultni id

            if (!postoji) {
                boolean uspjesno = dodajParkiraliste(p);
                if (uspjesno) {
                    odg.append("{\"odgovor\": [], \"status\": \"OK\"}");
                } else {
                    odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"").append(greska).append("\"}");
                }
            } else {
                odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"").append(greska).append("\"}");
            }
        } catch (Exception e) {
            odg = new StringBuilder();
            odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"Pogreška kod parsiranja odgovora!\"}");
        }
        greska = "";
        return odg.toString();
    }

    /**
     * Provjerava postoji li parkiralište s dobivenim id-om već u bazi
     *
     * @param id id parkirališta koji se provjerava
     * @return true ako postoji, false inače
     */
    private boolean postojiParkiraliste(int id) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return true;
        }

        String upit = "SELECT * FROM PARKIRALISTA WHERE id=" + id;
        Parkiraliste p = null;

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            ResultSet rs = stat.executeQuery();

            if (rs.next()) {
                Lokacija l = new Lokacija(rs.getString("latitude"), rs.getString("longitude"));
                p = new Parkiraliste(rs.getInt("id"), rs.getString("naziv"), rs.getString("adresa"), l);
            }

            rs.close();
            stat.close();
            con.close();

            if (p == null) {
                return false;
            } else {
                greska = "Parkiralište s id " + id + " već postoji!";
                return true;
            }
        } catch (SQLException ex) {
            greska = "Greška kod provjere parkirališta!";
            return true;
        }
    }

    /**
     * Dodaje parkiralište u bazu podataka.
     *
     * @param p parkiralište koje se dodaje u bazu podataka
     * @return true ako je uspješno dodano, false inače
     */
    private boolean dodajParkiraliste(Parkiraliste p) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return false;
        }

        Lokacija lok = null;
        try {
            lok = gmk.getGeoLocation(p.getAdresa());
        } catch (Exception e) {
            greska = "Greška pri dohvaćanju geolokacije!";
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
            greska = "Greška kod upisa parkirališta u bazu!";
            return false;
        }
    }

    /**
     * Operacija nije dozvoljena!
     *
     * @param podaci podaci koji se primaju
     * @return odgovor u json formatu
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public String putJson(String podaci) {
        return "{\"odgovor\": [], "
                + "\"status\": \"ERR\", \"poruka\": \"Nije dozvoljeno\"}";
    }

    /**
     * Operacije nije dozvoljena!
     *
     * @return odgovor u json formatu
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public String deleteJson() {
        return "{\"odgovor\": [], "
                + "\"status\": \"ERR\", \"poruka\": \"Nije dozvoljeno\"}";
    }

    /**
     * Dohvaća podatke izabranog parkirališta.
     *
     * @param id id po kojem se dohvaća parkiralište
     * @return odgovor u json formatu
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}")
    public String getJson(@PathParam("id") String id) {
        StringBuilder odg = new StringBuilder();
        try {
            Parkiraliste p = dajParkiraliste(Integer.parseInt(id));

            if (p != null) {
                String json = new Gson().toJson(p);
                odg.append("{\"odgovor\": [");
                odg.append(json);
                odg.append("], \"status\": \"OK\"}");
            } else {
                odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"").append(greska).append("\"}");
            }
        } catch (Exception e) {
            odg = new StringBuilder();
            odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"Pogreška kod parsiranja odgovora!\"}");
        }
        greska = "";
        return odg.toString();
    }

    /**
     * Dohvaća odabrano parkiralište iz baze podataka.
     *
     * @param id id po kojem se traži parkiralište
     * @return parkiralište iz baze, ili null ako ne postoji
     */
    private Parkiraliste dajParkiraliste(int id) {
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
                Lokacija l = new Lokacija(rs.getString("latitude"), rs.getString("longitude"));
                p = new Parkiraliste(rs.getInt("id"), rs.getString("naziv"), rs.getString("adresa"), l);
            }

            rs.close();
            stat.close();
            con.close();

            if (p == null) {
                greska = "Parkiralište s id " + id + " ne postoji!";
                return null;
            } else {
                return p;
            }
        } catch (SQLException ex) {
            greska = "Greška kod dohvaćanja parkirališta!";
            return null;
        }
    }

    /**
     * Operacija nije dozvoljena!
     *
     * @param id id parkirališta
     * @param podaci podaci o parkiralištu
     * @return odgovor u json formatu
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{id}")
    public String postJson(@PathParam("id") String id, String podaci) {
        return "{\"odgovor\": [], "
                + "\"status\": \"ERR\", \"poruka\": \"Nije dozvoljeno\"}";
    }

    /**
     * Ažurira podatke o parkiralištu u bazi podataka.
     *
     * @param id id parkirališta koje se ažurira
     * @param podaci novi podaci koje će parkiralište poprimiti
     * @return odgovor u json formatu
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{id}")
    public String putJson(@PathParam("id") String id, String podaci) {
        StringBuilder odg = new StringBuilder();
        try {
            int id2 = Integer.parseInt(id);
            Parkiraliste p = new Gson().fromJson(podaci, Parkiraliste.class);
            p.setId(id2);
            boolean nePostoji = nePostojiParkiraliste(p.getId());

            if (!nePostoji) {
                boolean uspjesno = azurirajParkiraliste(p);
                if (uspjesno) {
                    odg.append("{\"odgovor\": [], \"status\": \"OK\"}");
                } else {
                    odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"").append(greska).append("\"}");
                }
            } else {
                odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"").append(greska).append("\"}");
            }
        } catch (Exception e) {
            odg = new StringBuilder();
            odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"Pogreška kod parsiranja odgovora!\"}");
        }
        greska = "";
        return odg.toString();
    }

    /**
     * Provjerava ne postoji li parkiralište
     *
     * @param id id parkirališta koje se provjerava
     * @return true ako ne postoji, false inače
     */
    private boolean nePostojiParkiraliste(int id) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return true;
        }

        String upit = "SELECT * FROM PARKIRALISTA WHERE id=" + id;
        Parkiraliste p = null;

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            ResultSet rs = stat.executeQuery();

            if (rs.next()) {
                Lokacija l = new Lokacija(rs.getString("latitude"), rs.getString("longitude"));
                p = new Parkiraliste(rs.getInt("id"), rs.getString("naziv"), rs.getString("adresa"), l);
            }

            rs.close();
            stat.close();
            con.close();

            if (p == null) {
                greska = "Parkiralište s id " + id + " ne postoji!";
                return true;
            } else {
                return false;
            }
        } catch (SQLException ex) {
            greska = "Greška kod provjere parkirališta!";
            return true;
        }
    }

    /**
     * Ažurira podatke o parkiralištu u bazi podataka.
     *
     * @param p parkiralište koje se ažurira
     * @return true ako je uspješno ažurirano, false inače
     */
    private boolean azurirajParkiraliste(Parkiraliste p) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return false;
        }

        Lokacija lok = null;
        try {
            lok = gmk.getGeoLocation(p.getAdresa());
        } catch (Exception e) {
            greska = "Greška pri dohvaćanju geolokacije!";
            return false;
        }
        String upit = "UPDATE PARKIRALISTA SET naziv = '" + p.getNaziv() + "', "
                + "adresa = '" + p.getAdresa() + "', "
                + "latitude = " + lok.getLatitude() + ", "
                + "longitude = " + lok.getLongitude()
                + "WHERE id = " + p.getId();

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            stat.execute();
            stat.close();
            con.close();
            return true;
        } catch (SQLException ex) {
            greska = "Greška kod ažuriranja parkirališta u bazi!";
            return false;
        }
    }

    /**
     * Briše odabrano parkiralište ako još nema meteo podataka o njemu.
     *
     * @param id id parkirališta koje se želi obrisati
     * @return odgovor u json formatu
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}")
    public String deleteJson(@PathParam("id") String id) {
        StringBuilder odg = new StringBuilder();
        try {
            int id2 = Integer.parseInt(id);
            boolean nePostoji = nePostojiParkiraliste(id2);

            if (!nePostoji) {
                boolean uspjesno = obrisiParkiraliste(id2);
                if (uspjesno) {
                    odg.append("{\"odgovor\": [], \"status\": \"OK\"}");
                } else {
                    odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"").append(greska).append("\"}");
                }
            } else {
                odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"").append(greska).append("\"}");
            }
        } catch (Exception e) {
            odg = new StringBuilder();
            odg.append("{\"odgovor\": [], \"status\": \"ERR\", \"poruka\": \"Pogreška kod parsiranja odgovora!\"}");
        }
        greska = "";
        return odg.toString();
    }

    /**
     * Briše parkiralište iz baze
     *
     * @param id id parkirališta koje se briše
     * @return true ako je usješno obrisano, false inače
     */
    private boolean obrisiParkiraliste(int id) {
        if (!dohvatiPodatkeIzKonfiguracije()) {
            return false;
        }

        String upit = "DELETE FROM PARKIRALISTA WHERE id = " + id;

        try {
            con = DriverManager.getConnection(url, korIme, lozinka);
            PreparedStatement stat = con.prepareStatement(upit);
            stat.execute();
            stat.close();
            con.close();
            return true;
        } catch (SQLException ex) {
            greska = "Greška kod brisanja parkirališta u bazi!";
            return false;
        }
    }
}
