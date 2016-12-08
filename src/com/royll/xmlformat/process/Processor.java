package com.royll.xmlformat.process;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.royll.xmlformat.config.Config;
import com.royll.xmlformat.entity.ClassEntity;
import com.royll.xmlformat.entity.FieldEntity;
import com.royll.xmlformat.entity.IterableFieldEntity;
import com.royll.xmlformat.util.PsiClassUtil;
import org.apache.http.util.TextUtils;

import java.util.regex.Pattern;

import static com.royll.xmlformat.util.StringUtils.captureName;


/**
 * Created by dim on 16/11/7.
 */
public abstract class Processor {




    public static Processor getProcessor() {
        return new SimpleXmlProcessor();
    }

    public void process(ClassEntity classEntity, PsiElementFactory factory, PsiClass cls, IProcessor visitor) {
        onStarProcess(classEntity, factory, cls, visitor);

        for (FieldEntity fieldEntity : classEntity.getFields()) {
            generateField(factory, fieldEntity, cls, classEntity);
        }
        for (ClassEntity innerClass : classEntity.getInnerClasss()) {
            generateClass(factory, innerClass, cls, visitor);
        }
        generateGetterAndSetter(factory, cls, classEntity);
        generateConvertMethod(factory, cls, classEntity);
        onEndProcess(classEntity, factory, cls, visitor);

        injectAnnotation(factory, cls);
    }

    protected void onEndProcess(ClassEntity classEntity, PsiElementFactory factory, PsiClass cls, IProcessor visitor) {
        if (visitor != null) {
            visitor.onEndProcess(classEntity, factory, cls);
        }
        formatJavaFile(cls);
    }

    protected void formatJavaFile(PsiClass cls) {
        if (cls == null) {
            return;
        }
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(cls.getProject());
        styleManager.optimizeImports(cls.getContainingFile());
        styleManager.shortenClassReferences(cls);
    }

    protected void onStarProcess(ClassEntity classEntity, PsiElementFactory factory, PsiClass cls, IProcessor visitor) {
        if (visitor != null) {
            visitor.onStarProcess(classEntity, factory, cls);
        }
    }

    protected void generateConvertMethod(PsiElementFactory factory, PsiClass cls, ClassEntity classEntity) {
        if (Config.getInstant().isObjectFromData()) {
            createMethod(factory, Config.getInstant().getObjectFromDataStr().replace("$ClassName$", cls.getName()).trim(), cls);
        }
        if (Config.getInstant().isObjectFromData1()) {
            createMethod(factory, Config.getInstant().getObjectFromDataStr1().replace("$ClassName$", cls.getName()).trim(), cls);
        }
        if (Config.getInstant().isArrayFromData()) {
            createMethod(factory, Config.getInstant().getArrayFromDataStr().replace("$ClassName$", cls.getName()).trim(), cls);
        }
        if (Config.getInstant().isArrayFromData1()) {
            createMethod(factory, Config.getInstant().getArrayFromData1Str().replace("$ClassName$", cls.getName()).trim(), cls);
        }
    }

    protected void generateGetterAndSetter(PsiElementFactory factory, PsiClass cls, ClassEntity classEntity) {

        if (Config.getInstant().isFieldPrivateMode()) {
            for (FieldEntity field : classEntity.getFields()) {
                createGetAndSetMethod(factory, cls, field);
            }
        }
    }

    protected void createMethod(PsiElementFactory mFactory, String method, PsiClass cla) {
        cla.add(mFactory.createMethodFromText(method, cla));
    }

    protected void createGetAndSetMethod(PsiElementFactory factory, PsiClass cls, FieldEntity field) {
        if (field.isGenerate()) {
            String fieldName = field.getGenerateFieldName();
            String typeStr = field.getRealType();
            if (Config.getInstant().isUseFieldNamePrefix()) {
                String temp = fieldName.replaceAll("^" + Config.getInstant().getFiledNamePreFixStr(), "");
                if (!TextUtils.isEmpty(temp)) {
                    fieldName = temp;
                }
            }
            if (typeStr.equals("boolean") || typeStr.equals("Boolean")) {
                String method = "public ".concat(typeStr).concat("   is").concat(
                        captureName(fieldName)).concat("() {   return ").concat(
                        field.getGenerateFieldName()).concat(" ;} ");
                cls.add(factory.createMethodFromText(method, cls));
            } else {
                String method = "public ".concat(typeStr).concat(
                        "   get").concat(
                        captureName(fieldName)).concat(
                        "() {   return ").concat(
                        field.getGenerateFieldName()).concat(" ;} ");
                cls.add(factory.createMethodFromText(method, cls));
            }

            String arg = fieldName;
            if (Config.getInstant().isUseFieldNamePrefix()) {
                String temp = fieldName.replaceAll("^" + Config.getInstant().getFiledNamePreFixStr(), "");
                if (!TextUtils.isEmpty(temp)) {
                    fieldName = temp;
                    arg = fieldName;
                    if (arg.length() > 0) {

                        if (arg.length() > 1) {
                            arg = (arg.charAt(0) + "").toLowerCase() + arg.substring(1);
                        } else {
                            arg = arg.toLowerCase();
                        }
                    }
                }
            }

            String method = "public void  set".concat(captureName(fieldName)).concat("( ").concat(typeStr).concat(" ").concat(arg).concat(") {   ");
            if (field.getGenerateFieldName().equals(arg)) {
                method = method.concat("this.").concat(field.getGenerateFieldName()).concat(" = ").concat(arg).concat(";} ");
            } else {
                method = method.concat(field.getGenerateFieldName()).concat(" = ").concat(arg).concat(";} ");
            }
            cls.add(factory.createMethodFromText(method, cls));
        }
    }

    protected void generateClass(PsiElementFactory factory, ClassEntity classEntity, PsiClass parentClass, IProcessor visitor) {

        onStartGenerateClass(factory, classEntity, parentClass, visitor);
        PsiClass generateClass = null;
        if (classEntity.isGenerate()) {
            //// TODO: 16/11/9  待重构 
            if (Config.getInstant().isSplitGenerate()) {
                try {
                    generateClass = PsiClassUtil.getPsiClass(
                            parentClass.getContainingFile(), parentClass.getProject(), classEntity.getQualifiedName());
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            } else {
                String classNameElement = classEntity.getClassName();
                if (classNameElement.contains("Bean")) {
                    classNameElement = classNameElement.replace("Bean", "");
                }
                String classContent = Config.getInstant().getClassAnnotation().replaceAll("\\{filed\\}", classEntity.getKey()) + "\n" +
                        "public static class " + classEntity.getClassName() + "{}";
                generateClass = factory.createClassFromText(classContent, null).getInnerClasses()[0];
            }

            if (generateClass != null) {
                for (FieldEntity fieldEntity : classEntity.getFields()) {
                    generateField(factory, fieldEntity, generateClass, classEntity);
                }

                for (ClassEntity innerClass : classEntity.getInnerClasss()) {

                    generateClass(factory, innerClass, generateClass, visitor);
                }
                generateGetterAndSetter(factory, generateClass, classEntity);
                generateConvertMethod(factory, generateClass, classEntity);
                if (!Config.getInstant().isSplitGenerate()) {
                    parentClass.add(generateClass);
                }
            }

        }
        onEndGenerateClass(factory, classEntity, parentClass, generateClass, visitor);
        if (Config.getInstant().isSplitGenerate()) {
            formatJavaFile(generateClass);
        }

    }

    protected void onStartGenerateClass(PsiElementFactory factory, ClassEntity classEntity, PsiClass parentClass, IProcessor visitor) {
        if (visitor != null) {
            visitor.onStartGenerateClass(factory, classEntity, parentClass);
        }
    }

    protected void onEndGenerateClass(PsiElementFactory factory, ClassEntity classEntity, PsiClass parentClass, PsiClass generateClass, IProcessor visitor) {
        if (visitor != null) {
            visitor.onEndGenerateClass(factory, classEntity, parentClass, generateClass);
        }
    }

    protected void generateField(PsiElementFactory factory, FieldEntity fieldEntity, PsiClass cls, ClassEntity classEntity) {

        if (fieldEntity.isGenerate()) {

            StringBuilder fieldSb = new StringBuilder();
            String filedName = fieldEntity.getGenerateFieldName();
            if (!TextUtils.isEmpty(classEntity.getExtra())) {
                fieldSb.append(classEntity.getExtra()).append("\n");
                classEntity.setExtra(null);
            }
            if (fieldEntity.getTargetClass() != null) {
                fieldEntity.getTargetClass().setGenerate(true);
            }
            if (!filedName.equals(fieldEntity.getKey()) || Config.getInstant().isUseSerializedName()) {
                if (fieldEntity instanceof IterableFieldEntity) {
                    fieldSb.append(Config.getInstant().getFieldListAnnotation().replaceAll("\\{filed\\}", fieldEntity.getKey()));
                } else {
                    fieldSb.append(Config.getInstant().geFullNameAnnotation().replaceAll("\\{filed\\}", fieldEntity.getKey()));
                }
            }

            if (Config.getInstant().isFieldPrivateMode()) {
                System.out.println("fullnametype " + fieldEntity.getFullNameType());
                fieldSb.append("private  ").append(fieldEntity.getFullNameType()).append(" ").append(filedName).append(" ; ");
            } else {
                fieldSb.append("public  ").append(fieldEntity.getFullNameType()).append(" ").append(filedName).append(" ; ");
            }
            cls.add(factory.createFieldFromText(fieldSb.toString(), cls));
        }

    }

    private void injectAnnotation(PsiElementFactory factory, PsiClass generateClass) {
        if (factory == null || generateClass == null) {
            return;
        }
        PsiModifierList modifierList = generateClass.getModifierList();
        PsiElement firstChild = modifierList.getFirstChild();
        Pattern pattern = Pattern.compile("@.*?Root");
        if (firstChild != null && !pattern.matcher(firstChild.getText()).find()) {
            PsiAnnotation annotationFromText =
                    factory.createAnnotationFromText(Config.getInstant().getClassAnnotation().replaceAll("\\{filed\\}", Config.getInstant().getRootElementName()), generateClass);
            modifierList.addBefore(annotationFromText, firstChild);
        }
    }
}
