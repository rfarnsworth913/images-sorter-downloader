package com.imagesorter.ui;

import com.imagesorter.service.TagCatalog;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.text.*;

/** Exercises the real key actions and row renderer without opening native windows. */
public final class SelectorSmokeTest {
    private static Component popupContent;
    private static boolean popupVisible;
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("build"), "selector-test-");
        Path csv = directory.resolve("tags.csv");
        Files.writeString(csv, "type,parent,tag\npattern,,series\npattern,,filename\ncategory,,sfw\ncustom,,fan art\ncustom,,fan edit\n");
        try {
            TagCatalog catalog = TagCatalog.load(csv);
            SwingUtilities.invokeAndWait(() -> checkSelectors(catalog));
            TagCatalog reloaded = TagCatalog.load(csv);
            check(reloaded.knownTags().contains("fan"), "created tag survives restart");
            catalog.create("artist \"name\"");
            check(TagCatalog.load(csv).knownTags().contains("artist \"name\""), "CSV quotes round trip");
            int count=catalog.knownTags().size(); catalog.create("FAN"); check(catalog.knownTags().size()==count,"case-insensitive creation deduplicates");
            try { catalog.create("bad,tag"); throw new AssertionError("Invalid custom tag accepted"); } catch(IllegalArgumentException expected) { }
            System.out.println("PASS: selector filtering, highlighted Enter actions/hints, explicit creation, removal, path round trips, and CSV persistence.");
        } finally { Files.deleteIfExists(csv); Files.delete(directory); }
    }
    private static void checkSelectors(TagCatalog catalog) {
        ImageFlowTheme.install();
        PopupFactory original=PopupFactory.getSharedInstance();
        PopupFactory.setSharedInstance(new PopupFactory() {
            public Popup getPopup(Component owner,Component contents,int x,int y) {
                popupContent=contents;
                return new Popup() { public void show(){popupVisible=true;} public void hide(){popupVisible=false;} };
            }
        });
        JPanel anchor=new JPanel() {
            public boolean isShowing(){return true;}
            public Point getLocationOnScreen(){return new Point(100,100);}
            public GraphicsConfiguration getGraphicsConfiguration(){return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();}
        };
        anchor.setSize(420,42); JTextField input=new JTextField("fan"); anchor.add(input);
        List<TagSuggestions.Choice> activated=new ArrayList<>();
        TagSuggestions suggestions=new TagSuggestions(anchor,input,catalog::knownTags,input::getText,
                text -> TagCatalog.validCustom(text) && catalog.knownTags().stream().noneMatch(t->t.equalsIgnoreCase(text)), choice->{
                    activated.add(choice);
                    if(choice.create()) try {catalog.create(choice.tag());}catch(Exception e){throw new RuntimeException(e);}
                });
        try {
            suggestions.show(); JList<?> list=(JList<?>)find(popupContent,JList.class);
            check(list.getModel().getSize()==3,"two existing matches and separate create row");
            check(!((TagSuggestions.Choice)list.getSelectedValue()).create(),"existing match highlighted first");
            verifyHints(list);
            key(input,"ENTER");
            check(activated.size()==1 && activated.get(0).tag().equals("fan art") && !activated.get(0).create(),"Enter chooses highlighted existing tag");
            check(!catalog.knownTags().contains("fan"),"typing and existing selection do not create a custom tag");
            suggestions.show(); key(input,"DOWN"); verifyHints(list);
            check(((TagSuggestions.Choice)list.getSelectedValue()).tag().equals("fan edit"),"Down highlights next row");
            key(input,"DOWN"); verifyHints(list);
            check(((TagSuggestions.Choice)list.getSelectedValue()).create(),"create is separately highlighted");
            key(input,"ENTER"); check(activated.get(1).create() && catalog.knownTags().contains("fan"),"Enter activates highlighted create row");
            suggestions.show(); check(list.getModel().getSize()==3,"exact existing tag suppresses create action");
            key(input,"ESCAPE"); check(!popupVisible,"Escape dismisses suggestions");
            input.setText("sfw"); suggestions.show(); check(list.getModel().getSize()==1,"typing filters existing tags");
            suggestions.hide();
            TagPicker picker=new TagPicker(catalog,List.of("fan art","fan edit"),()->{});
            check(picker.selected().size()==2,"initial tags retained");
            JButton remove=(JButton)findButton(picker,"Remove fan art"); remove.doClick();
            check(picker.selected().equals(Set.of("fan edit")),"cross removes only its tag");
            JTextField model=new JTextField("{{series}}\\{{filename}}"); PatternEditor editor=new PatternEditor(catalog,model);
            check(editor.pattern().equals(model.getText()),"pattern tokens and separators round trip");
            JTextPane path=(JTextPane)find(editor,JTextPane.class);
            try { path.getDocument().insertString(path.getDocument().getLength(),"\\fan",null); }
            catch(BadLocationException e){throw new RuntimeException(e);}
            check(model.getText().equals("{{series}}\\{{filename}}\\fan"),"typing after a token stays literal");
            findButton(editor,"Remove {{series}}").doClick();
            check(model.getText().equals("\\{{filename}}\\fan"),"path cross removes the intended token only");
            model.setText("Archive/{{filename}} - {{###}}");
            check(editor.pattern().equals(model.getText()),"external pattern edits preserve text and counter tokens");
        } finally { suggestions.hide(); PopupFactory.setSharedInstance(original); }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void verifyHints(JList list) {
        for(int i=0;i<list.getModel().getSize();i++) {
            Component row=list.getCellRenderer().getListCellRendererComponent(list,list.getModel().getElementAt(i),i,i==list.getSelectedIndex(),false);
            check(hasLabel(row,"Enter")== (i==list.getSelectedIndex()),"Enter hint follows highlighted row");
        }
    }
    private static void key(JTextField input,String key) {
        Object binding=input.getInputMap().get(KeyStroke.getKeyStroke(key));
        input.getActionMap().get(binding).actionPerformed(new ActionEvent(input,ActionEvent.ACTION_PERFORMED,key));
    }
    private static Component find(Component root,Class<?> type) {
        if(type.isInstance(root))return root;
        if(root instanceof Container parent)for(Component child:parent.getComponents()){Component found=find(child,type);if(found!=null)return found;}
        return null;
    }
    private static JButton findButton(Component root,String tooltip) {
        if(root instanceof JButton button && tooltip.equals(button.getToolTipText()))return button;
        if(root instanceof Container parent)for(Component child:parent.getComponents()){JButton found=findButton(child,tooltip);if(found!=null)return found;}
        return null;
    }
    private static boolean hasLabel(Component root,String text) {
        if(root instanceof JLabel label && text.equals(label.getText()))return true;
        if(root instanceof Container parent)for(Component child:parent.getComponents())if(hasLabel(child,text))return true;
        return false;
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
