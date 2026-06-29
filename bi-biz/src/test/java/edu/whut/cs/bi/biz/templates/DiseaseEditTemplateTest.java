package edu.whut.cs.bi.biz.templates;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiseaseEditTemplateTest {

    @Test
    public void crackTypeInitializationTargetsTheActualEditControl() throws Exception {
        String template = Files.readString(
                Paths.get("src/main/resources/templates/biz/disease/edit.html"),
                StandardCharsets.UTF_8);

        assertTrue(template.contains("name=\"crackType\" id=\"crackType\""));
        assertTrue(template.contains("$(\"#crackType\").val(disease.crackType || \"纵向\")"));
        assertFalse(template.contains("select[name=\"diseaseDetails[0].crackType\"]"));
    }

    @Test
    public void crackTypeInitializationTargetsTheActualDetailControl() throws Exception {
        String template = Files.readString(
                Paths.get("src/main/resources/templates/biz/disease/detail.html"),
                StandardCharsets.UTF_8);

        assertTrue(template.contains("name=\"crackType\" id=\"crackType\" disabled"));
        assertTrue(template.contains("$(\"#crackType\").val(disease.crackType || \"纵向\")"));
        assertFalse(template.contains("select[name=\"diseaseDetails[0].crackType\"]"));
    }
}
