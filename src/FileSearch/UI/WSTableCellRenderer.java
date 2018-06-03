package FileSearch.UI;

import FileSearch.Core.WSFindTextResult;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;


public class WSTableCellRenderer extends JPanel implements TableCellRenderer {
    private final ColoredTableCellRenderer myUsageRenderer = new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {

            WSFindTextResult result = (WSFindTextResult)value;

            TextAttributesKey USAGE_LOCATION = TextAttributesKey.createTextAttributesKey("$NUMBER_OF_USAGES");
            EditorColorsScheme myColorsScheme = EditorColorsUtil.getColorSchemeForBackground(UIUtil.getTreeTextBackground());
            TextChunk textChunk = new TextChunk(myColorsScheme.getAttributes(USAGE_LOCATION),result.m_strLine);
            SimpleTextAttributes attributes = getAttributes(textChunk);
            myUsageRenderer.append(textChunk.getText(), attributes);

            // skip line number / file info
/*                for (int i = 1; i < text.length; ++i) {
                    TextChunk textChunk = text[i];
                    SimpleTextAttributes attributes = getAttributes(textChunk);
                    myUsageRenderer.append(textChunk.getText(), attributes);
                }*/
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

            WSFindTextResult result = (WSFindTextResult)value;
            VirtualFile file = result.m_virtualFile;
            //UsageInfo2UsageAdapter adapter = new UsageInfo2UsageAdapter(usageInfo);   //
            //String uniqueVirtualFilePath = getFilePath(adapter);
            String uniqueVirtualFilePath = file.getName();

            VirtualFile prevFile = findPrevFile(table, row, column);
            SimpleTextAttributes attributes = Comparing.equal(file, prevFile) ? REPEATED_FILE_ATTRIBUTES : ORDINAL_ATTRIBUTES;
            append(uniqueVirtualFilePath, attributes);

            append(" " + result.m_nLineIndex + 1, ORDINAL_ATTRIBUTES);
            setBorder(null);

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
        setBorder(JBUI.Borders.empty(MARGIN, MARGIN, MARGIN, 0));
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