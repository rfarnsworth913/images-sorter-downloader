package com.imagesorter.ui;

import com.imagesorter.service.*;
import java.awt.*;
import java.util.*;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import static com.imagesorter.ui.ImageFlowTheme.*;

/** Inline path tokens and literal text backed by the existing sort-pattern string. */
final class PatternEditor extends JPanel {
    private static final String TOKEN = "path-token";
    private final JTextPane input = new JTextPane();
    private final JTextField model;
    private final TagCatalog catalog;
    private final TagSuggestions suggestions;
    private boolean syncing;
    private boolean insertingToken;
    PatternEditor(TagCatalog catalog, JTextField model) {
        this.catalog = catalog; this.model = model;
        setLayout(new BorderLayout(2,0)); setBackground(INPUT); setBorder(new RoundedBorder(BORDER,10));
        input.setBackground(INPUT); input.setForeground(TEXT); input.setCaretColor(ACCENT);
        input.setBorder(BorderFactory.createEmptyBorder(5,5,5,5)); input.setFont(model.getFont());
        input.getAccessibleContext().setAccessibleName("Output folder and filename pattern");
        input.setToolTipText("Select tags, type folder separators or literal text, and remove tokens with the cross.");
        suggestions = new TagSuggestions(this, input, catalog::knownTags, this::query,
                text -> TagCatalog.validCustom(text) && catalog.knownTags().stream().noneMatch(t -> t.equalsIgnoreCase(text)) && validLiteral(text), choice -> {
                    try {
                        String tag = choice.create() ? catalog.create(choice.tag()) : choice.tag();
                        int start = queryStart(), end = input.getCaretPosition();
                        suggestionsEdit(start,end, tag);
                    } catch(Exception failure) { JOptionPane.showMessageDialog(this,failure.getMessage(),"Unable to insert tag",JOptionPane.ERROR_MESSAGE); }
                });
        JButton arrow = TagPicker.dropdownButton(); arrow.addActionListener(e -> suggestions.toggle());
        add(input,BorderLayout.CENTER); add(arrow,BorderLayout.EAST);
        ((AbstractDocument)input.getDocument()).setDocumentFilter(new DocumentFilter() {
            public void insertString(FilterBypass bypass,int offset,String text,AttributeSet attributes) throws BadLocationException {
                bypass.insertString(offset,text,insertingToken ? attributes : textStyle());
            }
            public void replace(FilterBypass bypass,int offset,int length,String text,AttributeSet attributes) throws BadLocationException {
                bypass.replace(offset,length,text,insertingToken ? attributes : textStyle());
            }
        });
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { update(); } public void removeUpdate(DocumentEvent e) { update(); } public void changedUpdate(DocumentEvent e) { }
            private void update() {
                if (syncing) return;
                syncing=true;
                try { model.setText(pattern()); } finally { syncing=false; }
                revalidate();
            }
        });
        model.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { update(); } public void removeUpdate(DocumentEvent e) { update(); } public void changedUpdate(DocumentEvent e) { }
            private void update() { if (!syncing) loadModel(); }
        });
        loadModel();
    }
    private static boolean validLiteral(String text) {
        try { return SortPattern.resolve(text,Map.of(),"sample.jpg",1).getNameCount()==1; }
        catch(IllegalArgumentException e) { return false; }
    }
    private void suggestionsEdit(int start,int end,String tag) throws BadLocationException {
        input.getDocument().remove(start,end-start);
        insertToken(start, catalog.patterns().contains(tag) ? "{{"+tag+"}}" : tag);
        input.setCaretPosition(start+1); input.setCharacterAttributes(textStyle(),true);
    }
    private SimpleAttributeSet textStyle() {
        SimpleAttributeSet style=new SimpleAttributeSet(); StyleConstants.setForeground(style,TEXT);
        StyleConstants.setFontFamily(style,input.getFont().getFamily()); StyleConstants.setFontSize(style,input.getFont().getSize()); return style;
    }
    private void insertToken(int offset,String value) throws BadLocationException {
        StyledDocument document=input.getStyledDocument();
        TokenChip[] chip = new TokenChip[1];
        chip[0]=new TokenChip(value,()->suggestions.edit(()-> {
            try {
                for(int i=0;i<document.getLength();i++) {
                    if(StyleConstants.getComponent(document.getCharacterElement(i).getAttributes())==chip[0]) {
                        document.remove(i,1); input.setCaretPosition(Math.min(i,document.getLength())); break;
                    }
                }
                input.requestFocusInWindow();
            }catch(BadLocationException e){throw new IllegalStateException(e);}
        }));
        SimpleAttributeSet attributes=new SimpleAttributeSet(); attributes.addAttribute(TOKEN,value); StyleConstants.setComponent(attributes,chip[0]);
        insertingToken=true;
        try { document.insertString(offset,"\uFFFC",attributes); } finally { insertingToken=false; }
    }
    String pattern() {
        StyledDocument document=input.getStyledDocument(); StringBuilder result=new StringBuilder();
        try {
            for(int i=0;i<document.getLength();i++) {
                Object value=document.getCharacterElement(i).getAttributes().getAttribute(TOKEN);
                result.append(value==null ? document.getText(i,1) : value.toString());
            }
        }catch(BadLocationException e){throw new IllegalStateException(e);}
        return result.toString();
    }
    private void loadModel() {
        syncing=true;
        try {
            StyledDocument document=input.getStyledDocument(); document.remove(0,document.getLength());
            String pattern=model.getText(); Matcher matcher=Pattern.compile("\\{\\{[^{}]+}}").matcher(pattern); int previous=0;
            while(matcher.find()) {
                document.insertString(document.getLength(),pattern.substring(previous,matcher.start()),textStyle());
                insertToken(document.getLength(),matcher.group()); previous=matcher.end();
            }
            document.insertString(document.getLength(),pattern.substring(previous),textStyle());
            input.setCaretPosition(document.getLength()); input.setCharacterAttributes(textStyle(),true);
        }catch(BadLocationException e){throw new IllegalStateException(e);}
        finally{syncing=false;}
    }
    private int queryStart() {
        try {
            String before=input.getDocument().getText(0,input.getCaretPosition()); int start=before.length();
            while(start>0) {
                char c=before.charAt(start-1); if(c=='\uFFFC'||c=='/'||c=='\\'||c=='{'||c=='}'||c=='\n') break; start--;
            }
            while(start<before.length() && Character.isWhitespace(before.charAt(start))) start++;
            return start;
        }catch(BadLocationException e){throw new IllegalStateException(e);}
    }
    private String query() {
        try { int start=queryStart(); return input.getDocument().getText(start,input.getCaretPosition()-start); }
        catch(BadLocationException e){throw new IllegalStateException(e);}
    }
    public Dimension getPreferredSize() {
        Dimension size=super.getPreferredSize(); size.height=Math.max(42,size.height); size.width=100; return size;
    }
}
