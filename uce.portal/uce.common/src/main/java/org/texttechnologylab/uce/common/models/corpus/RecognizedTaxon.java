package org.texttechnologylab.uce.common.models.corpus;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.texttechnologylab.uce.common.annotations.Typesystem;
import org.texttechnologylab.uce.common.models.UIMAAnnotation;
import org.texttechnologylab.uce.common.models.WikiModel;

import javax.persistence.*;
import java.util.*;
import java.util.function.Function;

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

	private String findBestMatchingProperty(Function<TaxonResolution, String> propertyExtractor) {
		LevenshteinDistance distance = LevenshteinDistance.getDefaultInstance();
		return this.resolutions.stream()
				.map(propertyExtractor)
				.filter(Objects::nonNull)
				.min(Comparator.comparingInt(s -> distance.apply(s, this.text)))
				.orElse(null);
	}

	public String findBestScientificName() {
		return findBestMatchingProperty(TaxonResolution::getScientificName);
	}

	public String findBestKingdomName() {
		return findBestMatchingProperty(TaxonResolution::getKingdomName);
	}

	private String mapKingdomToColor(String kingdom) {
		if (kingdom == null) {
			return null;
		}
		return switch (kingdom.toLowerCase()) {
			case "animalia" -> "blue";
			case "plantae" -> "green";
			case "fungi" -> "purple";
			case "protista" -> "orange";
			case "monera", "bacteria" -> "red";
			default -> null;
		};
	}

	public String generateCoverStartTag(boolean includeTitle) {
		String scientificName = findBestScientificName();
		String kingdomName = findBestKingdomName();
		String color = mapKingdomToColor(kingdomName);
		StringBuilder tagBuilder = new StringBuilder();
		tagBuilder.append("<span class='annotation recognized-taxon'");
		if (includeTitle) {
			tagBuilder.append(" title='").append(getText()).append("'");
		}
		tagBuilder.append(" data-wid='")
			.append(getWikiId())
			.append("' data-wcovered='")
			.append(getCoveredText())
			.append("' data-text='")
			.append(getText()).append("'");
		if (scientificName != null) {
			tagBuilder.append(" data-scientific-name='").append(scientificName).append("'");
		}
		if (kingdomName != null) {
			tagBuilder.append(" data-kingdom-name='").append(kingdomName).append("'");
		}
		if (color != null) {
			tagBuilder.append(" style='--taxon-color: ").append(color).append("'");
		}
		tagBuilder.append(">");
		return tagBuilder.toString();
	}

}
