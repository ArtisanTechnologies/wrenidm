package com.forgerock.openidm.web.component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.faces.event.ActionEvent;
import javax.faces.model.SelectItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SelectItem in both lists does have to contain values of the same type.
 *
 * @author lazyman
 */
public class DualList<T> implements Serializable {

    private static final Logger logger = LoggerFactory.getLogger(DualList.class);
    private List<SelectItem> leftItems = new ArrayList<SelectItem>();
    private List<T> selectedLeftItems = new ArrayList<T>();
    private List<SelectItem> rightItems = new ArrayList<SelectItem>();
    private List<T> selectedRightItems = new ArrayList<T>();
    private boolean listSorting = true;

    public boolean isListSorting() {
        return listSorting;
    }

    public void setListSorting(boolean listSorting) {
        this.listSorting = listSorting;
    }

    public List<SelectItem> getLeftItems() {
        return Collections.unmodifiableList(leftItems);
    }

    public List<T> getSelectedLeftItems() {
        return Collections.unmodifiableList(selectedLeftItems);
    }

    public List<SelectItem> getRightItems() {
        return Collections.unmodifiableList(rightItems);
    }

    public List<T> getSelectedRightItems() {
        return Collections.unmodifiableList(selectedRightItems);
    }

    public void setSelectedLeftItems(List<T> selectedLeftItems) {
        this.selectedLeftItems = selectedLeftItems;
    }

    public void setSelectedRightItems(List<T> selectedRightItems) {
        this.selectedRightItems = selectedRightItems;
    }

    public void moveRight(ActionEvent evt) {
        move(leftItems, rightItems, selectedLeftItems);
    }

    public void moveLeft(ActionEvent evt) {
        move(rightItems, leftItems, selectedRightItems);
    }

    public void moveAllRight(ActionEvent evt) {
        moveAll(leftItems, rightItems);
    }

    public void moveAllLeft(ActionEvent evt) {
        moveAll(rightItems, leftItems);
    }

    public void clear() {
        leftItems.clear();
        rightItems.clear();
        selectedLeftItems.clear();
        selectedRightItems.clear();
    }

    private void move(List<SelectItem> from, List<SelectItem> to, List<T> what) {
        Map<T, SelectItem> map = new HashMap();
        for (SelectItem item : from) {
            map.put((T) item.getValue().toString(), item);
        }
        for (T t : what) {
            SelectItem movedItem = map.get(t);
            if (movedItem == null) {
                throw new IllegalStateException("Can't move selected item '" + t + "': It can't be found in 'from' list.");
            }
            to.add(movedItem);
            from.remove(movedItem);
        }
        what.clear();

        if (isListSorting()) {
            Collections.sort(to, new SelectItemComparator());
        }
    }

    private void moveAll(List<SelectItem> from, List<SelectItem> to) {
        for (SelectItem item : from) {
            to.add(item);
        }

        if (from.equals(rightItems)) {
            selectedRightItems.clear();
        } else {
            selectedLeftItems.clear();
        }
        from.clear();

        if (isListSorting()) {
            Collections.sort(to, new SelectItemComparator());
        }
    }

    public void setLeftItems(List<SelectItem> itemList) {
        if (itemList == null) {
            throw new IllegalArgumentException("Left item list can't be null.");
        }

        leftItems = itemList;
        if (isListSorting()) {
            Collections.sort(leftItems, new SelectItemComparator());
        }
    }

    public void setRightItems(List<SelectItem> itemList) {
        if (itemList == null) {
            throw new IllegalArgumentException("Right item list can't be null.");
        }

        rightItems = itemList;
        if (isListSorting()) {
            Collections.sort(rightItems, new SelectItemComparator());
        }
    }

    private class SelectItemComparator implements Comparator<SelectItem> {

        @Override
        public int compare(SelectItem item1, SelectItem item2) {
            if (item1 == null || item2 == null) {
                logger.debug("Can't sort items in list because it contains null items.");
                return 0;
            }
            
            if (item1.getLabel() == null || item2.getLabel() == null) {
                logger.debug("Can't sort items in list because it contains items with null values.");
                return 0;
            }

            return String.CASE_INSENSITIVE_ORDER.compare(item1.getLabel(), item2.getLabel());
        }
    }
}

