/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package name.justinthomas.flower.analysis.persistence;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import java.util.TimeZone;
import javax.xml.bind.annotation.XmlType;

/**
 *
 * @author justin
 */
@Entity
@XmlType
public class PersistentUser {

    @PrimaryKey
    public String username;
    public String hashedPassword;
    public String fullName;
    public Boolean administrator;
    public String timeZone;

    private PersistentUser() { }

    public PersistentUser(String username, String hashedPassword, String fullName, Boolean administrator, String timeZone) {
        this.username = username;
        this.hashedPassword = hashedPassword;
        this.fullName = fullName;
        this.administrator = administrator;
        this.timeZone = timeZone;
    }
    
    public PersistentUser sanitize() {
        hashedPassword = null;
        return this;
    }
}
