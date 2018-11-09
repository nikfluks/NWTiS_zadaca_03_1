package org.foi.nwtis.nikfluks.web.podaci;

public class Parkiraliste {
    private int id;
    private String naziv;
    private String adresa;
    private Lokacija geolokacija;

    public Parkiraliste() {
    }

    public Parkiraliste(int id, String naziv, String adresa, Lokacija geoloc) {
        this.id = id;
        this.naziv = naziv;
        this.adresa = adresa;
        this.geolokacija = geoloc;
    }

    public Lokacija getGeolokacija() {
        return geolokacija;
    }

    public void setGeolokacija(Lokacija geolokacija) {
        this.geolokacija = geolokacija;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNaziv() {
        return naziv;
    }

    public void setNaziv(String naziv) {
        this.naziv = naziv;
    }      
	
    public String getAdresa() {
        return adresa;
    }

    public void setAdresa(String adresa) {
        this.adresa = adresa;
    }        
}
