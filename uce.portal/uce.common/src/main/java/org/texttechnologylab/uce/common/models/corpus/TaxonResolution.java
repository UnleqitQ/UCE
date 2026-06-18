package org.texttechnologylab.uce.common.models.corpus;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.texttechnologylab.uce.common.annotations.Taxonsystem;
import org.texttechnologylab.uce.common.annotations.Typesystem;
import org.texttechnologylab.uce.common.models.ModelBase;
import org.texttechnologylab.uce.common.models.UIMAAnnotation;
import org.texttechnologylab.uce.common.models.WikiModel;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Entity
@Typesystem(types = {org.texttechnologylab.annotation.type.TaxonResolution.class})
public class TaxonResolution extends ModelBase {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recognized_taxon_id", nullable = false)
	private RecognizedTaxon recognizedTaxonId;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String provider;

	@Column(nullable = false)
	private int taxonId;

	@Column(columnDefinition = "TEXT")
	private String kingdomName;
	@Column(nullable = false)
	private int kingdomId;

	@Column(columnDefinition = "TEXT")
	private String phylumName;
	@Column(nullable = false)
	private int phylumId;

	@Column(columnDefinition = "TEXT")
	private String className;
	@Column(nullable = false)
	private int classId;

	@Column(columnDefinition = "TEXT")
	private String orderName;
	@Column(nullable = false)
	private int orderId;

	@Column(columnDefinition = "TEXT")
	private String superfamilyName;
	@Column(nullable = false)
	private int superfamilyId;

	@Column(columnDefinition = "TEXT")
	private String familyName;
	@Column(nullable = false)
	private int familyId;

	@Column(columnDefinition = "TEXT")
	private String subfamilyName;
	@Column(nullable = false)
	private int subfamilyId;

	@Column(columnDefinition = "TEXT")
	private String tribeName;
	@Column(nullable = false)
	private int tribeId;

	@Column(columnDefinition = "TEXT")
	private String subtribeName;
	@Column(nullable = false)
	private int subtribeId;

	@Column(columnDefinition = "TEXT")
	private String genusName;
	@Column(nullable = false)
	private int genusId;

	@Column(columnDefinition = "TEXT")
	private String subgenusName;
	@Column(nullable = false)
	private int subgenusId;

	@Column(columnDefinition = "TEXT")
	private String speciesName;
	@Column(nullable = false)
	private int speciesId;

	@Column(columnDefinition = "TEXT")
	private String parentName;
	@Column(nullable = false)
	private int parentId;

	@Column(columnDefinition = "TEXT")
	private String scientificName;
	@Column(columnDefinition = "TEXT")
	private String canonicalName;
	@Column(columnDefinition = "TEXT")
	private String vernacularName;
	@Column(columnDefinition = "TEXT")
	private String acceptedName;

	@Column(columnDefinition = "TEXT")
	private String authorship;

	@Column(columnDefinition = "TEXT")
	private String nameType;

	@Column(columnDefinition = "TEXT")
	private String rank;

	@Column(columnDefinition = "TEXT")
	private String origin;

	@Column(columnDefinition = "TEXT")
	private String taxonomicStatus;

	@Column(columnDefinition = "TEXT")
	private String remarks;
	@Column(columnDefinition = "TEXT")
	private String references;
	@Column(columnDefinition = "TEXT")
	private String publishedIn;

	@Column(nullable = false)
	private int numDescendants;

	@Column(columnDefinition = "TEXT")
	private String lastCrawled;
	@Column(columnDefinition = "TEXT")
	private String lastInterpreted;

	@Column(columnDefinition = "TEXT")
	private String speciesEpithet;
	@Column(columnDefinition = "TEXT")
	private String infraspecificEpithet;
	@Column(columnDefinition = "TEXT")
	private String cultivarEpithet;

	@Column(columnDefinition = "TEXT")
	private String url;

	@Column(columnDefinition = "TEXT")
	private String wikidataId;
	@Column(columnDefinition = "TEXT")
	private String wikidataUrl;


	private void loadFromAnnotation(org.texttechnologylab.annotation.type.TaxonResolution annotation) {
		this.provider = annotation.getProvider();
		this.taxonId = annotation.getTaxonId();
		this.kingdomName = annotation.getKingdomName();
		this.kingdomId = annotation.getKingdomId();
		this.phylumName = annotation.getPhylumName();
		this.phylumId = annotation.getPhylumId();
		this.className = annotation.getClassName();
		this.classId = annotation.getClassId();
		this.orderName = annotation.getOrderName();
		this.orderId = annotation.getOrderId();
		this.superfamilyName = annotation.getSuperfamilyName();
		this.superfamilyId = annotation.getSuperfamilyId();
		this.familyName = annotation.getFamilyName();
		this.familyId = annotation.getFamilyId();
		this.subfamilyName = annotation.getSubfamilyName();
		this.subfamilyId = annotation.getSubfamilyId();
		this.tribeName = annotation.getTribeName();
		this.tribeId = annotation.getTribeId();
		this.subtribeName = annotation.getSubtribeName();
		this.subtribeId = annotation.getSubtribeId();
		this.genusName = annotation.getGenusName();
		this.genusId = annotation.getGenusId();
		this.subgenusName = annotation.getSubgenusName();
		this.subgenusId = annotation.getSubgenusId();
		this.speciesName = annotation.getSpeciesName();
		this.speciesId = annotation.getSpeciesId();
		this.parentName = annotation.getParentName();
		this.parentId = annotation.getParentId();
		this.scientificName = annotation.getScientificName();
		this.canonicalName = annotation.getCanonicalName();
		this.vernacularName = annotation.getVernacularName();
		this.acceptedName = annotation.getAcceptedNameUsage();
		this.authorship = annotation.getAuthorship();
		this.nameType = annotation.getNameType();
		this.rank = annotation.getRank();
		this.origin = annotation.getOrigin();
		this.taxonomicStatus = annotation.getTaxonomicStatus();
		this.remarks = annotation.getRemarks();
		this.references = annotation.getReferences();
		this.publishedIn = annotation.getPublishedIn();
		this.numDescendants = annotation.getNumDescendants();
		this.lastCrawled = annotation.getLastCrawled();
		this.lastInterpreted = annotation.getLastInterpreted();
		this.speciesEpithet = annotation.getSpeciesEpithet();
		this.infraspecificEpithet = annotation.getInfraspecificEpithet();
		this.cultivarEpithet = annotation.getCultivarEpithet();
		this.url = annotation.getUrl();
		this.wikidataId = annotation.getWikidataId();
		this.wikidataUrl = annotation.getWikidataUrl();
	}

}
