package org.texttechnologylab.uce.common.models.corpus;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.texttechnologylab.uce.common.annotations.Taxonsystem;
import org.texttechnologylab.uce.common.annotations.Typesystem;
import org.texttechnologylab.uce.common.models.UIMAAnnotation;
import org.texttechnologylab.uce.common.models.WikiModel;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Typesystem(types = {org.texttechnologylab.annotation.type.RecognizedTaxon.class})
public class RecognizedTaxon extends UIMAAnnotation implements WikiModel {
	@Getter
	@Setter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "document_id", nullable = false)
	private Document document;

	@Getter
	@Setter
	@Column(columnDefinition = "TEXT")
	private String text;

	@Setter
	@Getter
	@OneToMany(mappedBy = "recognizedTaxon", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
	@Fetch(value = FetchMode.SUBSELECT)
	private List<TaxonResolution> resolutions;

	public RecognizedTaxon() {
		super(-1, -1);
	}

	public RecognizedTaxon(int begin, int end) {
		super(begin, end);
	}

	@Override
	public String getWikiId() {
		return "RTx-%d".formatted(this.getId());
	}
}
