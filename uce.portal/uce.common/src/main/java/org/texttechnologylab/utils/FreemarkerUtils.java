package org.texttechnologylab.utils;

import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNumberModel;
import freemarker.template.TemplateSequenceModel;
import freemarker.template.TemplateModel;

import java.util.ArrayList;
import java.util.List;


public class FreemarkerUtils {
    public static List<List<Integer>> convertToNestedIntegerList(TemplateSequenceModel outerSeq)
            throws TemplateModelException {

        List<List<Integer>> result = new ArrayList<>();

        for (int i = 0; i < outerSeq.size(); i++) {
            TemplateModel innerModel = outerSeq.get(i);

            if (innerModel instanceof TemplateSequenceModel) {
                TemplateSequenceModel innerSeq = (TemplateSequenceModel) innerModel;
                List<Integer> innerList = new ArrayList<>();

                for (int j = 0; j < innerSeq.size(); j++) {
                    TemplateModel valueModel = innerSeq.get(j);
                    if (valueModel instanceof TemplateNumberModel) {
                        int val = ((TemplateNumberModel) valueModel).getAsNumber().intValue();
                        innerList.add(val);
                    } else {
                        throw new TemplateModelException("Expected number at [" + i + "][" + j + "]");
                    }
                }

                result.add(innerList);
            } else {
                throw new TemplateModelException("Expected sequence at index " + i);
            }
        }

        return result;
    }
}
