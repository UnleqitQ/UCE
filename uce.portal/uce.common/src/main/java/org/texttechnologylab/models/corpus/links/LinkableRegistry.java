package org.texttechnologylab.models.corpus.links;

import org.texttechnologylab.models.viewModels.link.LinkableViewModel;

import java.util.Map;
import java.util.WeakHashMap;

public class LinkableRegistry {
    // TODO: With millions of links cached, this might cause serious RAM problems (but the WeakHashMap should be cleaned up automatically, it's quiet fast at deleting).
    public static final Map<Object, LinkableViewModel> nodeMap = new WeakHashMap<>();
}
