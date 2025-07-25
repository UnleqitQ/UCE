package org.texttechnologylab.models.hate;

import lombok.Getter;
import lombok.Setter;
import org.texttechnologylab.models.ModelBase;

import javax.persistence.*;

@Entity
@Table(name = "hate_value")
public class HateValue extends ModelBase {

    @Getter
    @Setter
    @ManyToOne
    @JoinColumn(name = "hate_id", nullable = false)
    private Hate hate;

    @Getter
    @Setter
    @ManyToOne
    @JoinColumn(name = "type_id", nullable = false)
    private HateType hateType;

    @Getter
    @Setter
    @Column(name = "value", nullable = false)
    private double value;

    public HateValue() {
    }

}
