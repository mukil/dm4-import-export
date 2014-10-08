package net.abriraqui.dm4.importexport;

import de.deepamehta.core.model.CompositeValueModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.service.DeepaMehtaService;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;
import java.util.Hashtable;
import java.util.logging.Logger;

/**
 *
 * @author mukil
 */
public class AddressBookReader {
    
    // --- English Address Book Header Fields (first line of CSV export)
              
    final String THUNDERBIRD_EN_FIRST_NAME_KEY = "First Name";
    final String THUNDERBIRD_EN_LAST_NAME_KEY = "Last Name";
    final String THUNDERBIRD_EN_PRIMARY_EMAIL_KEY = "Primary Email";
    final String THUNDERBIRD_EN_MOBILE_PHONE_KEY = "Mobile Number";
    final String THUNDERBIRD_EN_HOME_PHONE_KEY = "Home Phone";
    final String THUNDERBIRD_EN_HOME_ADDRESS_KEY = "Home Address";
    final String THUNDERBIRD_EN_HOME_CITY_KEY = "Home City";
    final String THUNDERBIRD_EN_HOME_ZIPCODE_KEY = "Home ZipCode";
    final String THUNDERBIRD_EN_HOME_COUNTRY_KEY = "Home Country";
    final String THUNDERBIRD_EN_WORK_PHONE_KEY = "Work Phone";
    final String THUNDERBIRD_EN_WORK_JOB_KEY = "Job";
    final String THUNDERBIRD_EN_WORK_TITLE_KEY = "Title";
    final String THUNDERBIRD_EN_WORK_DEPARTMENT_KEY = "Department";
    final String THUNDERBIRD_EN_WORK_ORGANIZATION_KEY = "Organization";
    final String THUNDERBIRD_EN_WEBPAGE_ONE_KEY = "Web Page 1";
    final String THUNDERBIRD_EN_WEBPAGE_TWO_KEY = "Web Page 2";
    final String THUNDERBIRD_EN_NOTES_KEY = "Notes";
    
    // --- German Address Book Header Fields (first line of CSV export)
    
    final String THUNDERBIRD_DE_FIRST_NAME_KEY = "Vorname";
    final String THUNDERBIRD_DE_LAST_NAME_KEY = "Nachname";
    final String THUNDERBIRD_DE_PRIMARY_EMAIL_KEY = "PrimU+00E4re E-Mail-Adresse";
    final String THUNDERBIRD_DE_EMAIL_KEY_PART = "E-Mail-Adresse";
    final String THUNDERBIRD_DE_MOBILE_PHONE_KEY = "Mobil-Tel.-Nr.";
    final String THUNDERBIRD_DE_HOME_PHONE_KEY = "Tel. privat";
    final String THUNDERBIRD_DE_HOME_ADDRESS_KEY = "Privat: Adresse";
    final String THUNDERBIRD_DE_HOME_CITY_KEY = "Privat: Ort";
    final String THUNDERBIRD_DE_HOME_ZIPCODE_KEY = "Privat: PLZ";
    final String THUNDERBIRD_DE_HOME_COUNTRY_KEY = "Privat: Land";
    final String THUNDERBIRD_DE_WORK_PHONE_KEY = "Tel. dienstlich";
    final String THUNDERBIRD_DE_WORK_JOB_KEY = "Job";
    final String THUNDERBIRD_DE_WORK_TITLE_KEY = "Arbeitstitel";
    final String THUNDERBIRD_DE_WORK_DEPARTMENT_KEY = "Abteilung";
    final String THUNDERBIRD_DE_WORK_ORGANIZATION_KEY = "Organisation";
    final String THUNDERBIRD_DE_WEBPAGE_ONE_KEY = "Webseite 1";
    final String THUNDERBIRD_DE_WEBPAGE_TWO_KEY = "Webseite 2";
    final String THUNDERBIRD_DE_NOTES_KEY = "Notizen";
    
    // --- 
    
    Hashtable fieldIdxMap = null;
    
    // ---
    
    private Logger log = Logger.getLogger(getClass().getName());
    private DeepaMehtaService dms = null;
    
    public AddressBookReader(DeepaMehtaService dms) {
        this.dms = dms;
    }

    public void readInAddressBookFromTabCSV(String contact_data, String language) {
        // 
        String[] entries = getContactEntries(contact_data);
        String[] headers = entries[0].split("\t");
        // 
        if (language.equals("de")) {
            fieldIdxMap = getGermanThunderbirdFieldIdxMap(headers);
        } else if (language.equals("en")) {
            fieldIdxMap = getEnglishThunderbirdFieldIdxMap(headers);
        } else {
            log.severe("Language setting not supported.");
        }
        // 
        entries[0] = ""; // clear first line
        int nr = 0;
        for (String entry : entries) {
            if (!entry.equals("")) {
                // 
                CompositeValueModel personComposite = new CompositeValueModel();
                readInAddressLine(personComposite, entry);
                // 
                DeepaMehtaTransaction tx = dms.beginTx();
                try {
                    dms.createTopic(new TopicModel("dm4.contacts.person", personComposite));                    
                    tx.success();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    tx.finish();
                }
            }
        }
    }
    
    public void readInAddressLine(CompositeValueModel person, String addressLine) {
        CompositeValueModel personNameComposite = new CompositeValueModel();
        // CompositeValueModel personPhoneComposite = new CompositeValueModel();
        // CompositeValueModel personAddressComposite = new CompositeValueModel();
        String[] valueFields = getTabbedEntryFields(addressLine);
        for (int i = 0; i < valueFields.length; i++) {
            String value = valueFields[i];
            //
            if (value != null && !value.isEmpty()) {
                if (fieldIdxMap.get(i) == null) {
                    // no values here..
                } else if (fieldIdxMap.get(i).equals(THUNDERBIRD_EN_FIRST_NAME_KEY)) {
                    personNameComposite.put("dm4.contacts.first_name", value);
                } else if (fieldIdxMap.get(i).equals(THUNDERBIRD_EN_LAST_NAME_KEY)) {
                    personNameComposite.put("dm4.contacts.last_name", value);// set last name
                } else if (fieldIdxMap.get(i).toString().indexOf(THUNDERBIRD_EN_PRIMARY_EMAIL_KEY) != -1) {
                    // ## api inconsistencies with SimpleValue
                    person.add("dm4.contacts.email_address", 
                        new TopicModel("dm4.contacts.email_address", new SimpleValue(value)));
                } else if (fieldIdxMap.get(i).equals(THUNDERBIRD_EN_NOTES_KEY)) {
                    person.put("dm4.contacts.notes", value);
                } else if (fieldIdxMap.get(i).equals(THUNDERBIRD_EN_WEBPAGE_ONE_KEY)) { 
                    // ## api inconsistencies with SimpleValue
                    person.add("dm4.webbrowser.url", new TopicModel("dm4.webbrowser.url", 
                        new SimpleValue(value)));
                }
            }
        }
        person.put("dm4.contacts.person_name", personNameComposite);
        // personComposite.put("dm4.contacts.phone_entry", personPhoneComposite);
        // personComposite.put("dm4.contacts.address_entry", personAddressComposite);
    }
    
    private Hashtable getEnglishThunderbirdFieldIdxMap(String[] headerEntries) {
        Hashtable fieldIdxMap = new Hashtable();
        for (int i = 0; i < headerEntries.length; i++) {
            //
            String header = headerEntries[i];
            if (header.equals(THUNDERBIRD_EN_FIRST_NAME_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_FIRST_NAME_KEY);
            } else if (header.equals(THUNDERBIRD_EN_LAST_NAME_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_LAST_NAME_KEY);
            } else if (header.equals(THUNDERBIRD_EN_PRIMARY_EMAIL_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_PRIMARY_EMAIL_KEY);
            } else if (header.equals(THUNDERBIRD_EN_MOBILE_PHONE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_MOBILE_PHONE_KEY);
            } else if (header.equals(THUNDERBIRD_EN_HOME_PHONE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_HOME_PHONE_KEY);
            } else if (header.equals(THUNDERBIRD_EN_HOME_ADDRESS_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_HOME_ADDRESS_KEY);
            } else if (header.equals(THUNDERBIRD_EN_HOME_CITY_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_HOME_CITY_KEY);
            } else if (header.equals(THUNDERBIRD_EN_HOME_ZIPCODE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_HOME_ZIPCODE_KEY);
            } else if (header.equals(THUNDERBIRD_EN_HOME_COUNTRY_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_HOME_COUNTRY_KEY);
            } else if (header.equals(THUNDERBIRD_EN_WORK_PHONE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_WORK_PHONE_KEY);
            } else if (header.equals(THUNDERBIRD_EN_WORK_JOB_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_WORK_JOB_KEY);
            } else if (header.equals(THUNDERBIRD_EN_WORK_TITLE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_WORK_TITLE_KEY);
            } else if (header.equals(THUNDERBIRD_EN_WORK_DEPARTMENT_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_WORK_DEPARTMENT_KEY);
            } else if (header.equals(THUNDERBIRD_EN_WORK_ORGANIZATION_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_WORK_ORGANIZATION_KEY);
            } else if (header.equals(THUNDERBIRD_EN_WEBPAGE_ONE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_WEBPAGE_ONE_KEY);
            } else if (header.equals(THUNDERBIRD_EN_WEBPAGE_TWO_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_WEBPAGE_TWO_KEY);
            } else if (header.equals(THUNDERBIRD_EN_NOTES_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_EN_NOTES_KEY);
            }
        }
        return fieldIdxMap;
    }
    
     private Hashtable getGermanThunderbirdFieldIdxMap(String[] headerEntries) {
        Hashtable fieldIdxMap = new Hashtable();
        for (int i = 0; i < headerEntries.length; i++) {
            //
            String header = headerEntries[i];
            if (header.equals(THUNDERBIRD_DE_FIRST_NAME_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_FIRST_NAME_KEY);
            } else if (header.equals(THUNDERBIRD_DE_LAST_NAME_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_LAST_NAME_KEY);
            } else if (header.equals(THUNDERBIRD_DE_PRIMARY_EMAIL_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_PRIMARY_EMAIL_KEY);
            } else if (header.equals(THUNDERBIRD_DE_MOBILE_PHONE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_MOBILE_PHONE_KEY);
            } else if (header.equals(THUNDERBIRD_DE_HOME_PHONE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_HOME_PHONE_KEY);
            } else if (header.equals(THUNDERBIRD_DE_HOME_ADDRESS_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_HOME_ADDRESS_KEY);
            } else if (header.equals(THUNDERBIRD_DE_HOME_CITY_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_HOME_CITY_KEY);
            } else if (header.equals(THUNDERBIRD_DE_HOME_ZIPCODE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_HOME_ZIPCODE_KEY);
            } else if (header.equals(THUNDERBIRD_DE_HOME_COUNTRY_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_HOME_COUNTRY_KEY);
            } else if (header.equals(THUNDERBIRD_DE_WORK_PHONE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_WORK_PHONE_KEY);
            } else if (header.equals(THUNDERBIRD_DE_WORK_JOB_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_WORK_JOB_KEY);
            } else if (header.equals(THUNDERBIRD_DE_WORK_TITLE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_WORK_TITLE_KEY);
            } else if (header.equals(THUNDERBIRD_DE_WORK_DEPARTMENT_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_WORK_DEPARTMENT_KEY);
            } else if (header.equals(THUNDERBIRD_DE_WORK_ORGANIZATION_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_WORK_ORGANIZATION_KEY);
            } else if (header.equals(THUNDERBIRD_DE_WEBPAGE_ONE_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_WEBPAGE_ONE_KEY);
            } else if (header.equals(THUNDERBIRD_DE_WEBPAGE_TWO_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_WEBPAGE_TWO_KEY);
            } else if (header.equals(THUNDERBIRD_DE_NOTES_KEY)) {
                fieldIdxMap.put(i, THUNDERBIRD_DE_NOTES_KEY);
            }
        }
        return fieldIdxMap;
    }
    
    private String[] getContactEntries(String data) {
        return data.split("\n");
    }
    
    private String[] getTabbedEntryFields(String entry) {
        return entry.split("\t");
    }
    
}
