# Standards for EPUB3 Orientation Position Loss

## compose-ui/view-models

StateScreenModel (Voyager) survives configuration changes — the `Epub3ReaderState` instance is retained. This means `savedLocator` persists across rotations; the bug is not about ViewModel lifecycle but about stale view-level state (`EpubView.props.locator`) not being kept in sync.
