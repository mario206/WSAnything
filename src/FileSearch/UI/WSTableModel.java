package FileSearch.UI;

import FileSearch.FSLog;

import javax.swing.table.DefaultTableModel;
import java.util.Vector;

public class WSTableModel extends DefaultTableModel {
    public void addRows(Vector<Object> vec) {

        int begin = getRowCount();
        int end = begin + vec.size();

        for (int i = 0; i < vec.size(); ++i) {
            Object[] obj = new Object[]{vec.get(i)};
            __insertRow(getRowCount(), convertToVector(obj));
        }
        fireTableRowsInserted(begin, end);
    }

    public void __insertRow(int row, Vector rowData) {
        dataVector.insertElementAt(rowData, row);
        justifyRows(row, row + 1);
    }

    private void justifyRows(int from, int to) {
        // Sometimes the DefaultTableModel is subclassed
        // instead of the AbstractTableModel by mistake.
        // Set the number of rows for the case when getRowCount
        // is overridden.
        try {
            dataVector.setSize(getRowCount());

            for (int i = from; i < to; i++) {
                if (dataVector.elementAt(i) == null) {
                    dataVector.setElementAt(new Vector(), i);
                }
                ((Vector) dataVector.elementAt(i)).setSize(getColumnCount());
            }
        } catch (Exception e) {
            FSLog.log.info("sdfsd");
        }
    }
}
