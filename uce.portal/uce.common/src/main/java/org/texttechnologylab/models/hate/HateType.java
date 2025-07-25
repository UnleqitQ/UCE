package org.texttechnologylab.models.hate;

import lombok.Getter;
import lombok.Setter;
import org.texttechnologylab.models.ModelBase;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "hate_type")
public class HateType extends ModelBase {

    @Getter
    @Setter
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    public HateType() {}

    public HateType(String name) {
        this.name = name;
    }

}
