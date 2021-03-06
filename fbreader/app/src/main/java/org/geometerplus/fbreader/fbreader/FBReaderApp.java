/*
 * Copyright (C) 2007-2017 FBReader.ORG Limited <contact@fbreader.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.fbreader.fbreader;

import org.fbreader.common.options.SyncOptions;
import org.fbreader.util.ComparisonUtil;
import org.geometerplus.fbreader.book.Book;
import org.geometerplus.fbreader.book.BookEvent;
import org.geometerplus.fbreader.book.BookUtil;
import org.geometerplus.fbreader.book.Bookmark;
import org.geometerplus.fbreader.book.BookmarkQuery;
import org.geometerplus.fbreader.book.BookmarkUtil;
import org.geometerplus.fbreader.book.IBookCollection;
import org.geometerplus.fbreader.bookmodel.BookModel;
import org.geometerplus.fbreader.bookmodel.TOCTree;
import org.geometerplus.fbreader.fbreader.options.CancelMenuHelper;
import org.geometerplus.fbreader.fbreader.options.ImageOptions;
import org.geometerplus.fbreader.fbreader.options.MiscOptions;
import org.geometerplus.fbreader.fbreader.options.PageTurningOptions;
import org.geometerplus.fbreader.fbreader.options.ViewOptions;
import org.geometerplus.fbreader.formats.BookReadingException;
import org.geometerplus.fbreader.formats.ExternalFormatPlugin;
import org.geometerplus.fbreader.formats.FormatPlugin;
import org.geometerplus.fbreader.formats.PluginCollection;
import org.geometerplus.fbreader.network.sync.SyncData;
import org.geometerplus.fbreader.util.AutoTextSnippet;
import org.geometerplus.fbreader.util.EmptyTextSnippet;
import org.geometerplus.fbreader.util.TextSnippet;
import org.geometerplus.zlibrary.core.application.ZLApplication;
import org.geometerplus.zlibrary.core.application.ZLKeyBindings;
import org.geometerplus.zlibrary.core.drm.EncryptionMethod;
import org.geometerplus.zlibrary.core.drm.FileEncryptionInfo;
import org.geometerplus.zlibrary.core.util.RationalNumber;
import org.geometerplus.zlibrary.core.util.SystemInfo;
import org.geometerplus.zlibrary.text.hyphenation.ZLTextHyphenator;
import org.geometerplus.zlibrary.text.model.ZLTextModel;
import org.geometerplus.zlibrary.text.view.ZLTextFixedPosition;
import org.geometerplus.zlibrary.text.view.ZLTextParagraphCursor;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.text.view.ZLTextPositionWithTimestamp;
import org.geometerplus.zlibrary.text.view.ZLTextView;
import org.geometerplus.zlibrary.text.view.ZLTextWordCursor;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FBReaderApp extends ZLApplication implements IBookCollection.Listener<Book> {
    public final MiscOptions MiscOptions = new MiscOptions();
    public final ImageOptions ImageOptions = new ImageOptions();
    public final ViewOptions ViewOptions = new ViewOptions();
    public final PageTurningOptions PageTurningOptions = new PageTurningOptions();
    public final SyncOptions SyncOptions = new SyncOptions();
    public final FBView BookTextView;
    public final FBView FootnoteView;
    public final IBookCollection<Book> Collection;
    final PositionManager myPositionManager = new PositionManager();
    private final ZLKeyBindings myBindings = new ZLKeyBindings();
    private final SyncData mySyncData = new SyncData();
    private final ExecutorService mySaver = Executors.newSingleThreadExecutor();
    public volatile BookModel Model;
    public volatile Book ExternalBook;
    private ExternalFileOpener myExternalFileOpener;
    private String myFootnoteModelId;
    private volatile boolean myOpenInProgress = false;
    private ZLTextPosition myJumpEndPosition;
    private Date myJumpTimeStamp;

    public FBReaderApp(SystemInfo systemInfo, final IBookCollection<Book> collection) {
        super(systemInfo);

        Collection = collection;
        collection.addListener(this);

        addAction(ActionCode.INCREASE_FONT, new ChangeFontSizeAction(this, +2));
        addAction(ActionCode.DECREASE_FONT, new ChangeFontSizeAction(this, -2));

        addAction(ActionCode.FIND_NEXT, new FindNextAction(this));
        addAction(ActionCode.FIND_PREVIOUS, new FindPreviousAction(this));
        addAction(ActionCode.CLEAR_FIND_RESULTS, new ClearFindResultsAction(this));

        addAction(ActionCode.SELECTION_CLEAR, new SelectionClearAction(this));

        addAction(ActionCode.TURN_PAGE_FORWARD, new TurnPageAction(this, true));
        addAction(ActionCode.TURN_PAGE_BACK, new TurnPageAction(this, false));

        addAction(ActionCode.MOVE_CURSOR_UP, new MoveCursorAction(this, FBView.Direction.up));
        addAction(ActionCode.MOVE_CURSOR_DOWN, new MoveCursorAction(this, FBView.Direction.down));
        addAction(ActionCode.MOVE_CURSOR_LEFT, new MoveCursorAction(this, FBView.Direction.rightToLeft));
        addAction(ActionCode.MOVE_CURSOR_RIGHT, new MoveCursorAction(this, FBView.Direction.leftToRight));

        addAction(ActionCode.VOLUME_KEY_SCROLL_FORWARD, new VolumeKeyTurnPageAction(this, true));
        addAction(ActionCode.VOLUME_KEY_SCROLL_BACK, new VolumeKeyTurnPageAction(this, false));

        addAction(ActionCode.EXIT, new ExitAction(this));

        BookTextView = new FBView(this);
        FootnoteView = new FBView(this);

        setView(BookTextView);
    }

    public void setExternalFileOpener(ExternalFileOpener o) {
        myExternalFileOpener = o;
    }

    public Book getCurrentBook() {
        final BookModel m = Model;
        return m != null ? m.Book : ExternalBook;
    }

    public void openHelpBook() {
        openBook(Collection.getBookByFile(BookUtil.getHelpFile().getPath()), null, null, null);
    }

    public Book getCurrentServerBook(Notifier notifier) {
        final SyncData.ServerBookInfo info = mySyncData.getServerBookInfo();
        if (info == null) {
            return null;
        }

        for (String hash : info.Hashes) {
            final Book book = Collection.getBookByHash(hash);
            if (book != null) {
                return book;
            }
        }
        if (notifier != null) {
            notifier.showMissingBookNotification(info);
        }
        return null;
    }

    public void openBook(Book book, final Bookmark bookmark, Runnable postAction, Notifier notifier) {
        if (Model != null) {
            if (book == null || bookmark == null && Collection.sameBook(book, Model.Book)) {
                return;
            }
        }

        if (book == null) {
            book = getCurrentServerBook(notifier);
            if (book == null) {
                book = Collection.getRecentBook(0);
            }
            if (book == null || !BookUtil.fileByBook(book).exists()) {
                book = Collection.getBookByFile(BookUtil.getHelpFile().getPath());
            }
            if (book == null) {
                return;
            }
        }
        final Book bookToOpen = book;
        bookToOpen.addNewLabel(Book.READ_LABEL);
        Collection.saveBook(bookToOpen);

        final SynchronousExecutor executor = createExecutor("loadingBook");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                openBookInternal(bookToOpen, bookmark, false);
            }
        }, postAction);
    }

    @Override
    public ZLKeyBindings keyBindings() {
        return myBindings;
    }

    public FBView getTextView() {
        return (FBView) getCurrentView();
    }

    public AutoTextSnippet getFootnoteData(String id) {
        if (Model == null) {
            return null;
        }
        final BookModel.Label label = Model.getLabel(id);
        if (label == null) {
            return null;
        }
        final ZLTextModel model;
        if (label.ModelId != null) {
            model = Model.getFootnoteModel(label.ModelId);
        } else {
            model = Model.getTextModel();
        }
        if (model == null) {
            return null;
        }
        final ZLTextWordCursor cursor =
                new ZLTextWordCursor(new ZLTextParagraphCursor(model, label.ParagraphIndex));
        final AutoTextSnippet longSnippet = new AutoTextSnippet(cursor, 140);
        if (longSnippet.IsEndOfText) {
            return longSnippet;
        } else {
            return new AutoTextSnippet(cursor, 100);
        }
    }

    public void tryOpenFootnote(String id) {
        if (Model != null) {
            myJumpEndPosition = null;
            myJumpTimeStamp = null;
            final BookModel.Label label = Model.getLabel(id);
            if (label != null) {
                if (label.ModelId == null) {
                    if (getTextView() == BookTextView) {
                        addInvisibleBookmark();
                        myJumpEndPosition = new ZLTextFixedPosition(label.ParagraphIndex, 0, 0);
                        myJumpTimeStamp = new Date();
                    }
                    BookTextView.gotoPosition(label.ParagraphIndex, 0, 0);
                    setView(BookTextView);
                } else {
                    setFootnoteModel(label.ModelId);
                    setView(FootnoteView);
                    FootnoteView.gotoPosition(label.ParagraphIndex, 0, 0);
                }
                getViewWidget().repaint();
                storePosition();
            }
        }
    }

    public void clearTextCaches() {
        BookTextView.clearCaches();
        FootnoteView.clearCaches();
    }

    public Bookmark addSelectionBookmark() {
        final FBView fbView = getTextView();
        final TextSnippet snippet = fbView.getSelectedSnippet();
        if (snippet == null) {
            return null;
        }

        final Bookmark bookmark = new Bookmark(
                Collection,
                Model.Book,
                fbView.getModel().getId(),
                snippet,
                true
        );
        Collection.saveBookmark(bookmark);
        fbView.clearSelection();

        return bookmark;
    }

    private void setBookmarkHighlightings(ZLTextView view, String modelId) {
        view.removeHighlightings(BookmarkHighlighting.class);
        for (BookmarkQuery query = new BookmarkQuery(Model.Book, 20); ; query = query.next()) {
            final List<Bookmark> bookmarks = Collection.bookmarks(query);
            if (bookmarks.isEmpty()) {
                break;
            }
            for (Bookmark b : bookmarks) {
                if (b.getEnd() == null) {
                    BookmarkUtil.findEnd(b, view);
                }
                if (ComparisonUtil.equal(modelId, b.ModelId)) {
                    view.addHighlighting(new BookmarkHighlighting(view, Collection, b));
                }
            }
        }
    }

    private void setFootnoteModel(String modelId) {
        final ZLTextModel model = Model.getFootnoteModel(modelId);
        FootnoteView.setModel(model);
        if (model != null) {
            myFootnoteModelId = modelId;
            setBookmarkHighlightings(FootnoteView, modelId);
        }
    }

    private synchronized void openBookInternal(final Book book, Bookmark bookmark, boolean force) {
        if (!force && Model != null && Collection.sameBook(book, Model.Book)) {
            if (bookmark != null) {
                gotoBookmark(bookmark, false);
            }
            return;
        }

        hideActivePopup();
        storePosition();

        BookTextView.setModel(null);
        FootnoteView.setModel(null);
        clearTextCaches();
        Model = null;
        ExternalBook = null;
        System.gc();
        System.gc();

        final PluginCollection pluginCollection = PluginCollection.Instance(SystemInfo);
        final FormatPlugin plugin;
        try {
            plugin = BookUtil.getPlugin(pluginCollection, book);
        } catch (BookReadingException e) {
            processException(e);
            return;
        }

        if (plugin instanceof ExternalFormatPlugin) {
            ExternalBook = book;
            final Bookmark bm;
            if (bookmark != null) {
                bm = bookmark;
            } else {
                final ZLTextPositionWithTimestamp pos = getStoredPosition(book);
                bm = new Bookmark(Collection, book, "", new EmptyTextSnippet(pos.Position), false);
            }
            myExternalFileOpener.openFile((ExternalFormatPlugin) plugin, book, bm);
            return;
        }

        try {
            try {
                myOpenInProgress = true;
                Model = BookModel.createModel(book, plugin);
                Collection.saveBook(book);
                ZLTextHyphenator.Instance().load(book.getLanguage());
                BookTextView.setModel(Model.getTextModel());
                setBookmarkHighlightings(BookTextView, null);

                final ZLTextPositionWithTimestamp local =
                        myPositionManager.getLocallyStoredPosition(book);
                final ZLTextPositionWithTimestamp remote =
                        mySyncData.getAndCleanPosition(Collection.getHash(book, true));

                if (remote == null) {
                    if (local != null) {
                        BookTextView.gotoPosition(local.Position);
                    }
                } else {
                    if (local == null || local.Timestamp < remote.Timestamp) {
                        BookTextView.gotoPosition(remote.Position);
                        savePosition(book, remote, BookTextView.getProgress());
                    } else {
                        BookTextView.gotoPosition(local.Position);
                    }
                }
            } finally {
                myOpenInProgress = false;
            }

            if (bookmark == null) {
                setView(BookTextView);
            } else {
                gotoBookmark(bookmark, false);
            }
            Collection.addToRecentlyOpened(book);
            final StringBuilder title = new StringBuilder(book.getTitle());
            final String authorsString = book.authorsString(", ");
            if (authorsString != null) {
                title.append(" (").append(authorsString).append(")");
            }
            setTitle(title.toString());
        } catch (BookReadingException e) {
            processException(e);
        }

        getViewWidget().reset();
        getViewWidget().repaint();

        for (FileEncryptionInfo info : plugin.readEncryptionInfos(book)) {
            if (info != null && !EncryptionMethod.isSupported(info.Method)) {
                showErrorMessage("unsupportedEncryptionMethod", book.getPath());
                break;
            }
        }
    }

    private List<Bookmark> invisibleBookmarks() {
        final List<Bookmark> bookmarks = Collection.bookmarks(
                new BookmarkQuery(Model.Book, false, 10)
        );
        Collections.sort(bookmarks, new Bookmark.ByTimeComparator());
        return bookmarks;
    }

    public boolean jumpBack() {
        try {
            if (getTextView() != BookTextView) {
                showBookTextView();
                return true;
            }

            if (myJumpEndPosition == null || myJumpTimeStamp == null) {
                return false;
            }
            // more than 2 minutes ago
            if (myJumpTimeStamp.getTime() + 2 * 60 * 1000 < new Date().getTime()) {
                return false;
            }
            if (!myJumpEndPosition.equals(BookTextView.getStartCursor())) {
                return false;
            }

            final List<Bookmark> bookmarks = invisibleBookmarks();
            if (bookmarks.isEmpty()) {
                return false;
            }
            final Bookmark b = bookmarks.get(0);
            Collection.deleteBookmark(b);
            gotoBookmark(b, true);
            return true;
        } finally {
            myJumpEndPosition = null;
            myJumpTimeStamp = null;
        }
    }

    private void gotoBookmark(Bookmark bookmark, boolean exactly) {
        final String modelId = bookmark.ModelId;
        if (modelId == null) {
            addInvisibleBookmark();
            if (exactly) {
                BookTextView.gotoPosition(bookmark);
            } else {
                BookTextView.gotoHighlighting(
                        new BookmarkHighlighting(BookTextView, Collection, bookmark)
                );
            }
            setView(BookTextView);
        } else {
            setFootnoteModel(modelId);
            if (exactly) {
                FootnoteView.gotoPosition(bookmark);
            } else {
                FootnoteView.gotoHighlighting(
                        new BookmarkHighlighting(FootnoteView, Collection, bookmark)
                );
            }
            setView(FootnoteView);
        }
        getViewWidget().repaint();
        storePosition();
    }

    public void showBookTextView() {
        setView(BookTextView);
    }

    @Override
    public void onWindowClosing() {
        storePosition();
    }

    public void useSyncInfo(boolean openOtherBook, Notifier notifier) {
        if (openOtherBook && SyncOptions.ChangeCurrentBook.getValue()) {
            final Book fromServer = getCurrentServerBook(notifier);
            if (fromServer != null && !Collection.sameBook(fromServer, Collection.getRecentBook(0))) {
                openBook(fromServer, null, null, notifier);
                return;
            }
        }

        final Book book = Model != null ? Model.Book : null;
        if (book == null) {
            return;
        }

        final ZLTextPositionWithTimestamp remote =
                mySyncData.getAndCleanPosition(Collection.getHash(book, true));
        if (remote == null) {
            return;
        }

        final ZLTextPositionWithTimestamp local =
                myPositionManager.getLocallyStoredPosition(book);
        if (local == null || local.Timestamp < remote.Timestamp) {
            BookTextView.gotoPosition(remote.Position);
            savePosition(book, remote, BookTextView.getProgress());
        }
    }

    private ZLTextPositionWithTimestamp getStoredPosition(Book book) {
        final ZLTextPositionWithTimestamp fromServer =
                mySyncData.getAndCleanPosition(Collection.getHash(book, true));
        final ZLTextPositionWithTimestamp local =
                myPositionManager.getLocallyStoredPosition(book);
        myPositionManager.resetPositionCache(book);

        if (local == null) {
            return fromServer != null ? fromServer : new ZLTextPositionWithTimestamp(0, 0, 0, System.currentTimeMillis());
        } else if (fromServer == null) {
            return local;
        } else {
            return fromServer.Timestamp >= local.Timestamp ? fromServer : local;
        }
    }

    public void storePosition() {
        if (myOpenInProgress) {
            return;
        }

        final Book book = Model != null ? Model.Book : null;
        if (book == null || BookTextView == null) {
            return;
        }

        ZLTextPositionWithTimestamp local =
                myPositionManager.getLocallyStoredPosition(book);
        final ZLTextFixedPosition position =
                new ZLTextFixedPosition(BookTextView.getStartCursor());

        if (local == null || !local.Position.equals(position)) {
            local = new ZLTextPositionWithTimestamp(position, System.currentTimeMillis());
            savePosition(book, local, BookTextView.getProgress());
        }
    }

    private void savePosition(Book book, ZLTextPositionWithTimestamp position, RationalNumber progress) {
        synchronized (mySaver) {
            mySaver.execute(new PositionSaver(book, position, progress));
        }
    }

    public boolean hasCancelActions() {
        return new CancelMenuHelper().getActionsList(Collection).size() > 1;
    }

    public void runCancelAction(CancelMenuHelper.ActionType type, Bookmark bookmark) {
        switch (type) {
            case library:
                runAction(ActionCode.SHOW_LIBRARY);
                break;
            case networkLibrary:
                runAction(ActionCode.SHOW_NETWORK_LIBRARY);
                break;
            case previousBook:
                openBook(Collection.getRecentBook(1), null, null, null);
                break;
            case returnTo:
                Collection.deleteBookmark(bookmark);
                gotoBookmark(bookmark, true);
                break;
            case close:
                closeWindow();
                break;
        }
    }

    private synchronized void updateInvisibleBookmarksList(Bookmark b) {
        if (Model != null && Model.Book != null && b != null) {
            for (Bookmark bm : invisibleBookmarks()) {
                if (b.equals(bm)) {
                    Collection.deleteBookmark(bm);
                }
            }
            Collection.saveBookmark(b);
            final List<Bookmark> bookmarks = invisibleBookmarks();
            for (int i = 3; i < bookmarks.size(); ++i) {
                Collection.deleteBookmark(bookmarks.get(i));
            }
        }
    }

    public void addInvisibleBookmark(ZLTextWordCursor cursor) {
        if (cursor == null) {
            return;
        }

        cursor = new ZLTextWordCursor(cursor);
        if (cursor.isNull()) {
            return;
        }

        final ZLTextView textView = getTextView();
        final ZLTextModel textModel;
        final Book book;
        final AutoTextSnippet snippet;
        // textView.model will not be changed inside synchronised block
        synchronized (textView) {
            textModel = textView.getModel();
            final BookModel model = Model;
            book = model != null ? model.Book : null;
            if (book == null || textView != BookTextView || textModel == null) {
                return;
            }
            snippet = new AutoTextSnippet(cursor, 30);
        }

        updateInvisibleBookmarksList(new Bookmark(
                Collection, book, textModel.getId(), snippet, false
        ));
    }

    public void addInvisibleBookmark() {
        if (Model != null && Model.Book != null && getTextView() == BookTextView) {
            updateInvisibleBookmarksList(createBookmark(30, false));
        }
    }

    public Bookmark createBookmark(int maxChars, boolean visible) {
        final FBView view = getTextView();
        final ZLTextWordCursor cursor = view.getStartCursor();

        if (cursor.isNull()) {
            return null;
        }

        return new Bookmark(
                Collection,
                Model.Book,
                view.getModel().getId(),
                new AutoTextSnippet(cursor, maxChars),
                visible
        );
    }

    public TOCTree getCurrentTOCElement() {
        final ZLTextWordCursor cursor = BookTextView.getStartCursor();
        if (Model == null || cursor == null) {
            return null;
        }

        int index = cursor.getParagraphIndex();
        if (cursor.isEndOfParagraph()) {
            ++index;
        }
        TOCTree treeToSelect = null;
        for (TOCTree tree : Model.TOCTree) {
            final TOCTree.Reference reference = tree.getReference();
            if (reference == null) {
                continue;
            }
            if (reference.ParagraphIndex > index) {
                break;
            }
            treeToSelect = tree;
        }
        return treeToSelect;
    }

    public void onBookUpdated(Book book) {
        final BookModel model = Model;
        if (model == null) {
            return;
        }

        final Book current = model.Book;
        if (current == null || !Collection.sameBook(current, book)) {
            return;
        }

        final String newEncoding = book.getEncodingNoDetection();
        final String oldEncoding = current.getEncodingNoDetection();

        current.updateFrom(book);

        if (newEncoding != null && !newEncoding.equals(oldEncoding)) {
            final SynchronousExecutor executor = createExecutor("loadingBook");
            executor.execute(new Runnable() {
                public void run() {
                    openBookInternal(current, null, true);
                }
            }, null);
        } else {
            ZLTextHyphenator.Instance().load(current.getLanguage());
            clearTextCaches();
            getViewWidget().repaint();
        }
    }

    @Override
    public void onBookEvent(BookEvent event, Book book) {
        switch (event) {
            case BookmarkStyleChanged:
            case BookmarksUpdated:
                if (Model != null && (book == null || Collection.sameBook(book, Model.Book))) {
                    if (BookTextView.getModel() != null) {
                        setBookmarkHighlightings(BookTextView, null);
                    }
                    if (FootnoteView.getModel() != null && myFootnoteModelId != null) {
                        setBookmarkHighlightings(FootnoteView, myFootnoteModelId);
                    }
                }
                break;
            case Updated:
                onBookUpdated(book);
                break;
        }
    }

    @Override
    public void onBuildEvent(IBookCollection.Status status) {
    }

    public interface ExternalFileOpener {
        public void openFile(ExternalFormatPlugin plugin, Book book, Bookmark bookmark);
    }

    public static interface Notifier {
        void showMissingBookNotification(SyncData.ServerBookInfo info);
    }

    private class PositionSaver implements Runnable {
        private final Book myBook;
        private final ZLTextPositionWithTimestamp myPosition;
        private final RationalNumber myProgress;

        PositionSaver(Book book, ZLTextPositionWithTimestamp position, RationalNumber progress) {
            myBook = book;
            myPosition = position;
            myProgress = progress;
        }

        @Override
        public void run() {
            myPositionManager.storePositionLocally(myBook, myPosition);
            myBook.setProgress(myProgress);
            Collection.saveBook(myBook);
        }
    }

    private class PositionManager {
        private final ZLTextPositionWithTimestamp NULL_POSITION =
                new ZLTextPositionWithTimestamp(0, 0, 0, null);

        private final Map<Book, ZLTextPositionWithTimestamp> myPositions =
                Collections.synchronizedMap(new HashMap<Book, ZLTextPositionWithTimestamp>());

        ZLTextPositionWithTimestamp getLocallyStoredPosition(Book book) {
            if (book == null) {
                return null;
            }

            synchronized (myPositions) {
                ZLTextPositionWithTimestamp pos = myPositions.get(book);
                if (pos == null) {
                    pos = Collection.getStoredPosition(book.getId());
                    myPositions.put(book, pos != null ? pos : NULL_POSITION);
                }
                return pos != NULL_POSITION ? pos : null;
            }
        }

        void resetPositionCache(Book book) {
            if (book != null) {
                myPositions.remove(book);
            }
        }

        void storePositionLocally(Book book, ZLTextPositionWithTimestamp position) {
            if (book == null || position == null) {
                return;
            }
            synchronized (myPositions) {
                final ZLTextPositionWithTimestamp old = getLocallyStoredPosition(book);
                if (old != null && position.Position.equals(old.Position)) {
                    return;
                }
                myPositions.put(book, position);
                Collection.storePosition(book.getId(), position);
            }
        }
    }
}
