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
        assertTrue(template.contains("initializeCrackType(selectedDiseaseType)"));
        assertFalse(template.contains("select[name=\"diseaseDetails[0].crackType\"]"));
    }

    @Test
    public void crackTypeUsesTheQuantityFieldWidthAndSpecialTypeRemarkOptions() throws Exception {
        String template = Files.readString(
                Paths.get("src/main/resources/templates/biz/disease/edit.html"),
                StandardCharsets.UTF_8);

        assertTrue(template.contains("class=\"quantity-grid detail-toolbar\""));
        assertTrue(template.contains("parseCrackTypesFromRemark(selectedDiseaseType.remark)"));
        assertTrue(template.contains("buildCrackTypeOptions(selectedDiseaseType)"));
        assertTrue(template.contains("isOriginalDiseaseType(selectedDiseaseType)"));
        assertTrue(template.contains("options.push(savedCrackType)"));
        assertFalse(template.contains("<option value=\"纵向\">纵向</option>"));
    }

    @Test
    public void specialLayoutRendersSavedLengthPartsSeparately() throws Exception {
        String template = Files.readString(
                Paths.get("src/main/resources/templates/biz/disease/edit.html"),
                StandardCharsets.UTF_8);

        assertTrue(template.contains("getSpecialLengthPartCount("));
        assertTrue(template.contains("special-length-input length-parts-"));
        assertTrue(template.contains("diseaseDetails[${i}].length2"));
        assertTrue(template.contains("diseaseDetails[${i}].length3"));
        assertTrue(template.contains("lengthParts.join('+')"));
    }

    @Test
    public void umj3bCrackTypeControlsSpecialLengthPartCount() throws Exception {
        String template = Files.readString(
                Paths.get("src/main/resources/templates/biz/disease/edit.html"),
                StandardCharsets.UTF_8);

        assertTrue(template.contains("=== 'UMJ-3(B)'"));
        assertTrue(template.contains("normalizedCrackType === 'U+UMJ+U') return 3"));
        assertTrue(template.contains("normalizedCrackType === 'UMJ+U') return 2"));
        assertTrue(template.contains("updateSpecialLengthFieldsForCrackType($(this).val())"));
        assertTrue(template.contains("diseaseDetails[' + detailIndex + '].length3"));
    }

    @Test
    public void specialLayoutUsesDedicatedDiseaseDescriptionRules() throws Exception {
        String template = Files.readString(
                Paths.get("src/main/resources/templates/biz/disease/edit.html"),
                StandardCharsets.UTF_8);

        assertTrue(template.contains("{'横隔板': 'H', '纵隔板': 'Z'}"));
        assertTrue(template.contains("{'顶部U肋': 'U', '底部U肋': 'DU'}"));
        assertTrue(template.contains("function generateSpecialDiseaseDescription"));
        assertTrue(template.contains("reference2Text += '-' + reference2EndSequence"));
        assertTrue(template.contains("reference2Text += formatSpecialSignedOffset(reference2)"));
        assertTrue(template.contains("quantitativeParts.push('L='"));
        assertTrue(template.contains("quantitativeParts.push('W='"));
        assertTrue(template.contains("quantitativeParts.push('S='"));
        assertTrue(template.contains("quantitativeParts.push('D='"));
        assertTrue(template.contains("if (specialQuantitativeLayout)"));
    }

    @Test
    public void uRibDeformationDescriptionPlacesSeverityAndCrackBeforeDiseaseName() throws Exception {
        String template = Files.readString(
                Paths.get("src/main/resources/templates/biz/disease/edit.html"),
                StandardCharsets.UTF_8);

        assertTrue(template.contains("=== 'U肋变形(鼓曲)'"));
        assertTrue(template.contains("if (deformationAmount < 1) return '轻度'"));
        assertTrue(template.contains("if (deformationAmount < 4) return '中度'"));
        assertTrue(template.contains("return '重度'"));
        assertTrue(template.contains("getURibDeformationSeverity(detailCard) + String(crackType || '') + diseaseName"));
    }

    @Test
    public void fastenerDiseaseGroupsUseOptionalQuantityWithoutCreatingMultipleDetails() throws Exception {
        String template = Files.readString(
                Paths.get("src/main/resources/templates/biz/disease/edit.html"),
                StandardCharsets.UTF_8);

        assertTrue(template.contains("normalizedName === '铆钉(螺栓)缺失'"));
        assertTrue(template.contains("normalizedName === '铆钉(螺栓)松动'"));
        assertTrue(template.contains("isFastenerCountGroup(getDiseaseGroupName(selectedDiseaseType))"));
        assertTrue(template.contains("isFastenerCountGroup(getDiseaseGroupName(getSelectedDiseaseType()))"));
        assertTrue(template.contains("var isFastenerCount = isCurrentFastenerCountDisease()"));
        assertFalse(template.contains("isFastenerCountDisease(diseaseName)"));
        assertTrue(template.contains("quantity = specialQuantitativeLayout ? 1 : enteredQuantity"));
        assertTrue(template.contains("if (specialQuantitativeLayout || quantity < threshold)"));
        assertTrue(template.contains("quantityInput.prop('required', !specialQuantitativeLayout)"));
        assertTrue(template.contains("data-fastener-empty=\"true\""));
        assertTrue(template.contains("getFastenerDescriptionName(diseaseName) + countAndUnit"));
        assertFalse(template.contains("if (!isCurrentFastenerCountDisease())"));
        assertTrue(template.contains("formData.set(\"fastenerCountMode\", \"true\")"));
        assertTrue(template.contains("formData.set(\"quantity\", \"0\")"));
    }

    @Test
    public void specialLayoutHidesQuantityFieldsOutsideFastenerGroups() throws Exception {
        String template = Files.readString(
                Paths.get("src/main/resources/templates/biz/disease/edit.html"),
                StandardCharsets.UTF_8);

        assertTrue(template.contains("id=\"quantityFields\""));
        assertTrue(template.contains("var hideQuantityFields = specialQuantitativeLayout && !fastenerCountMode"));
        assertTrue(template.contains("quantityFields.toggle(!hideQuantityFields)"));
        assertTrue(template.contains("quantityInput.prop('disabled', hideQuantityFields)"));
        assertTrue(template.contains("unitsSelect.prop('disabled', hideQuantityFields)"));
        assertTrue(template.contains("if (input.disabled)"));
    }

    @Test
    public void specialReferenceDropdownsDoNotOfferCustomValues() throws Exception {
        String template = Files.readString(
                Paths.get("src/main/resources/templates/biz/disease/edit.html"),
                StandardCharsets.UTF_8);
        int specialFillStart = template.indexOf("function fillSpecialReferenceDropdowns");
        int normalFillStart = template.indexOf("function fillReferenceDropdowns", specialFillStart);
        String specialFill = template.substring(specialFillStart, normalFillStart);

        assertFalse(specialFill.contains(".val('其他').text('其他')"));
        assertFalse(specialFill.contains("customInput"));
        assertFalse(template.contains("special-reference-select\" name=\"${selectName}\" id=\"${selectId}\" onchange"));
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
