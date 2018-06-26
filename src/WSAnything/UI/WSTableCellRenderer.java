package WSAnything.UI;

import WSAnything.Core.WSFindTextResult;
import WSAnything.Core.WSUtil;
import WSAnything.FSLog;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class WSTableCellRenderer extends JPanel implements TableCellRenderer {
    private final ColoredTableCellRenderer myUsageRenderer = new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
            WSFindTextResult result = (WSFindTextResult)value;
            UsageInfo2UsageAdapter adapter = WSUtil.getMergedUsageAdapter(result);

 /*           UsageInfo usageIn = WSUtil.getUsageInfo((WSFindTextResult) result);
            UsageInfo2UsageAdapter adapter = new UsageInfo2UsageAdapter(usageInfo);*/

/*            TextAttributesKey USAGE_LOCATION = TextAttributesKey.createTextAttributesKey("$NUMBER_OF_USAGES");
            EditorColorsScheme myColorsScheme = EditorColorsUtil.getColorSchemeForBackground(UIUtil.getTreeTextBackground());
            TextChunk textChunk = new TextChunk(myColorsScheme.getAttributes(USAGE_LOCATION),result.m_strLine);
            SimpleTextAttributes attributes = getAttributes(textChunk);
            myUsageRenderer.append(textChunk.getText(), attributes);

            // skip line number / file info
*//*                for (int i = 1; i < text.length; ++i) {
                    TextChunk textChunk = text[i];
                    SimpleTextAttributes attributes = getAttributes(textChunk);
                    myUsageRenderer.append(textChunk.getText(), attributes);
                }*//*
            setBorder(null);*/


            TextChunk[] text = adapter.getPresentation().getText();

            // skip line number / file info
            for (int i = 1; i < text.length; ++i) {
                TextChunk textChunk = text[i];
                SimpleTextAttributes attributes = getAttributes(textChunk);
                //myUsageRenderer.append(textChunk.getText(), attributes);
                append(textChunk.getText(), attributes);
            }
            setBorder(null);
        }

        @NotNull
        private SimpleTextAttributes getAttributes(@NotNull TextChunk textChunk) {
            SimpleTextAttributes at = textChunk.getSimpleAttributesIgnoreBackground();
            if (myUseBold) return at;
            boolean highlighted = textChunk.getType() != null || at.getFontStyle() == Font.BOLD;
            return highlighted
                    ? new SimpleTextAttributes(null, at.getFgColor(), at.getWaveColor(),
                    (at.getStyle() & ~SimpleTextAttributes.STYLE_BOLD) |
                            SimpleTextAttributes.STYLE_SEARCH_MATCH)
                    : at;
        }
    };
    private final ColoredTableCellRenderer myFileAndLineNumber = new ColoredTableCellRenderer() {
        private final SimpleTextAttributes REPEATED_FILE_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, new JBColor(0xCCCCCC, 0x5E5E5E));
        private final SimpleTextAttributes ORDINAL_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, new JBColor(0x999999, 0x999999));

        @Override
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {

/*            WSFindTextResult result = (WSFindTextResult)value;
            VirtualFile file = result.m_virtualFile;
            //UsageInfo2UsageAdapter adapter = new UsageInfo2UsageAdapter(usageInfo);   //
            //String uniqueVirtualFilePath = getFilePath(adapter);
            String uniqueVirtualFilePath = file.getName();

            VirtualFile prevFile = findPrevFile(table, row, column);
            SimpleTextAttributes attributes = Comparing.equal(file, prevFile) ? REPEATED_FILE_ATTRIBUTES : ORDINAL_ATTRIBUTES;
            append(uniqueVirtualFilePath, attributes);

            append(" " + result.m_nLineIndex + 1, ORDINAL_ATTRIBUTES);
            setBorder(null);*/

            WSFindTextResult result = (WSFindTextResult)value;
            VirtualFile file = result.m_virtualFile;

            VirtualFile prevFile = findPrevFile(table, row, column);
            SimpleTextAttributes attributes = Comparing.equal(file, prevFile) ? REPEATED_FILE_ATTRIBUTES : ORDINAL_ATTRIBUTES;

            if(!result.m_bConsiderFileName) {
                // fileName is normally display
                append(result.m_strFileName, attributes);
            } else {
                /// split fileName to different parts : the highlight part / normal part
                List<Pair<String,Boolean>> list = splitFileName(result.m_strFileName,result.m_ListMatchFileNameIndex);
                for(int i = 0;i < list.size();++i) {
                    Pair<String,Boolean> pair = list.get(i);
                    append(pair.first,getAttributes(attributes,pair.second));
                }
            }

            append(" " + (result.m_nLineIndex + 1), ORDINAL_ATTRIBUTES);
            setBorder(null);
        }

        private SimpleTextAttributes getAttributes(SimpleTextAttributes at,boolean bMatch) {
            if (myUseBold) return at;
            boolean highlighted = bMatch || at.getFontStyle() == Font.BOLD;
            return highlighted
                    ? new SimpleTextAttributes(null, at.getFgColor(), at.getWaveColor(),
                    (at.getStyle() & ~SimpleTextAttributes.STYLE_BOLD) |
                            SimpleTextAttributes.STYLE_SEARCH_MATCH)
                    : at;
        }
        /// split fileName to different parts : the highlight part / normal part
        private List<Pair<String,Boolean>> splitFileName(String fileName,List<Pair<Integer, Integer>> m_ListMatchFileNameIndex) {
            List<Pair<String,Boolean>> result = new ArrayList<>();  // true if highlight

            int length = fileName.length();
            Boolean[] flags = new Boolean[length];

            for(int i = 0;i < length;++i) flags[i] = false;

            for(int i = 0;i < m_ListMatchFileNameIndex.size();++i) {
                Pair<Integer,Integer> pair = m_ListMatchFileNameIndex.get(i);
                for(int j = pair.first;j <= pair.second;++j) {
                    flags[j] = true;
                }
            }

            int lastIndex = 0;
            Boolean lastFlag = flags[0];


            for(int i = 1;i < length;++i) {
                /// split [lastIndex,i]
                if(lastFlag != flags[i]) {
                    String tmp = fileName.substring(lastIndex,i);
                    result.add(new Pair<>(tmp,lastFlag));
                    lastFlag = flags[i];
                    lastIndex = i;
                }
            }

            String tmp = fileName.substring(lastIndex,length);
            result.add(new Pair<>(tmp,lastFlag));

            return result;
        }

        @NotNull
        private String getFilePath(@NotNull UsageInfo2UsageAdapter ua) {
            String uniquePath =
                    UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(ua.getUsageInfo().getProject(), ua.getFile(), myScope);
            return myOmitFileExtension ? FileUtilRt.getNameWithoutExtension(uniquePath) : uniquePath;
        }

        @Nullable
        private VirtualFile findPrevFile(@NotNull JTable table, int row, int column) {
            if (row <= 0) return null;
            Object prev = table.getValueAt(row - 1, column);
            return prev instanceof UsageInfo2UsageAdapter ? ((UsageInfo2UsageAdapter)prev).getFile() : null;
        }
    };

    private static final int MARGIN = 2;
    private final boolean myOmitFileExtension;
    private final boolean myUseBold;
    private final GlobalSearchScope myScope;

    public WSTableCellRenderer(boolean omitFileExtension, boolean useBold, GlobalSearchScope scope) {
        myOmitFileExtension = omitFileExtension;
        myUseBold = useBold;
        myScope = scope;
        setLayout(new BorderLayout());
        add(myUsageRenderer, BorderLayout.CENTER);
        add(myFileAndLineNumber, BorderLayout.EAST);
        setBorder(JBUI.Borders.empty(MARGIN, MARGIN, MARGIN, MARGIN));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        myUsageRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        myFileAndLineNumber.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setBackground(myUsageRenderer.getBackground());
        if (!isSelected && value instanceof UsageInfo2UsageAdapter) {
            UsageInfo2UsageAdapter usageAdapter = (UsageInfo2UsageAdapter)value;
            Project project = usageAdapter.getUsageInfo().getProject();
            //mariotodo
            Color color = Color.BLACK;

            //Color color = VfsPresentationUtil.getFileBackgroundColor(project, usageAdapter.getFile());
            setBackground(color);
            myUsageRenderer.setBackground(color);
            myFileAndLineNumber.setBackground(color);
        }
        return this;
    }
}