use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Deserialize, Serialize, PartialEq, Eq)]
pub struct Entry {
    pub title: String,
    pub url: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Tab {
    pub id: String,
    pub url: String,
    pub title: String,
    pub loading: bool,
    pub progress: u8,
    pub can_go_back: bool,
    pub can_go_forward: bool,
}

impl Tab {
    fn new(id: String, url: String) -> Self {
        Self {
            id,
            url,
            title: String::new(),
            loading: false,
            progress: 0,
            can_go_back: false,
            can_go_forward: false,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct SearchEngine {
    pub name: &'static str,
    pub template: &'static str,
}

pub const SEARCH_ENGINES: [SearchEngine; 4] = [
    SearchEngine {
        name: "DuckDuckGo",
        template: "https://duckduckgo.com/?q=%s",
    },
    SearchEngine {
        name: "Kagi",
        template: "https://kagi.com/search?q=%s",
    },
    SearchEngine {
        name: "Brave Search",
        template: "https://search.brave.com/search?q=%s",
    },
    SearchEngine {
        name: "Startpage",
        template: "https://www.startpage.com/sp/search?query=%s",
    },
];

#[derive(Debug)]
pub struct BrowserState {
    pub tabs: Vec<Tab>,
    pub active_id: String,
    pub bookmarks: Vec<Entry>,
    pub history: Vec<Entry>,
    pub search_engine: usize,
    next_tab_id: u64,
}

impl BrowserState {
    pub fn restore(
        tabs: Vec<String>,
        active_index: usize,
        bookmarks: Vec<Entry>,
        mut history: Vec<Entry>,
        search_engine: usize,
    ) -> Self {
        history.truncate(100);
        let mut index = 0;
        while index < history.len() {
            let duplicate = history[..index]
                .iter()
                .any(|entry| same_page_url(&entry.url, &history[index].url));
            if duplicate {
                history.remove(index);
            } else {
                index += 1;
            }
        }
        let mut state = Self {
            tabs: Vec::with_capacity(tabs.len().max(1)),
            active_id: String::new(),
            bookmarks,
            history,
            search_engine: search_engine.min(SEARCH_ENGINES.len() - 1),
            next_tab_id: 1,
        };
        for url in tabs {
            let id = state.allocate_tab_id();
            state.tabs.push(Tab::new(id, url));
        }
        if state.tabs.is_empty() {
            state.new_tab();
        } else {
            let index = active_index.min(state.tabs.len() - 1);
            state.active_id.clone_from(&state.tabs[index].id);
        }
        state
    }

    pub fn current(&self) -> Option<&Tab> {
        self.tabs.iter().find(|tab| tab.id == self.active_id)
    }

    pub fn current_mut(&mut self) -> Option<&mut Tab> {
        self.tabs.iter_mut().find(|tab| tab.id == self.active_id)
    }

    fn allocate_tab_id(&mut self) -> String {
        let id = format!("tab-{}", self.next_tab_id);
        self.next_tab_id = self.next_tab_id.wrapping_add(1).max(1);
        id
    }

    pub fn new_tab(&mut self) -> &Tab {
        let id = self.allocate_tab_id();
        self.tabs.push(Tab::new(id.clone(), String::new()));
        self.active_id = id;
        self.current().expect("new tab is active")
    }

    pub fn select_tab(&mut self, id: &str) -> bool {
        if self.tabs.iter().any(|tab| tab.id == id) {
            self.active_id.clear();
            self.active_id.push_str(id);
            true
        } else {
            false
        }
    }

    pub fn close_tab(&mut self, id: &str) -> bool {
        let Some(index) = self.tabs.iter().position(|tab| tab.id == id) else {
            return false;
        };
        let was_active = self.active_id == id;
        self.tabs.remove(index);
        if self.tabs.is_empty() {
            self.new_tab();
        } else if was_active {
            self.active_id
                .clone_from(&self.tabs[index.min(self.tabs.len() - 1)].id);
        }
        true
    }

    pub fn navigate(&mut self, input: &str) -> Option<String> {
        let url = resolve_address(input, SEARCH_ENGINES[self.search_engine].template);
        if url.is_empty() {
            return None;
        }
        let tab = self.current_mut()?;
        tab.url.clone_from(&url);
        tab.title.clear();
        tab.loading = true;
        tab.progress = 0;
        Some(url)
    }

    pub fn go_home(&mut self) {
        if let Some(tab) = self.current_mut() {
            tab.url.clear();
            tab.title.clear();
            tab.loading = false;
            tab.progress = 0;
            tab.can_go_back = false;
            tab.can_go_forward = false;
        }
    }

    pub fn add_bookmark(&mut self) -> bool {
        let Some(tab) = self.current() else {
            return false;
        };
        if tab.url.is_empty()
            || self
                .bookmarks
                .iter()
                .any(|entry| same_page_url(&entry.url, &tab.url))
        {
            return false;
        }
        self.bookmarks.insert(
            0,
            Entry {
                title: if tab.title.is_empty() {
                    tab.url.clone()
                } else {
                    tab.title.clone()
                },
                url: tab.url.clone(),
            },
        );
        true
    }

    pub fn record_visit(&mut self, tab_id: &str) {
        let Some(tab) = self.tabs.iter().find(|tab| tab.id == tab_id) else {
            return;
        };
        if tab.url.is_empty() || tab.url == "about:blank" {
            return;
        }
        if let Some(index) = self
            .history
            .iter()
            .position(|entry| same_page_url(&entry.url, &tab.url))
        {
            self.history.remove(index);
        }
        self.history.insert(
            0,
            Entry {
                title: if tab.title.is_empty() {
                    tab.url.clone()
                } else {
                    tab.title.clone()
                },
                url: tab.url.clone(),
            },
        );
        self.history.truncate(100);
    }
    pub fn update_saved_titles(&mut self, tab_id: &str) -> bool {
        let Some(tab) = self.tabs.iter().find(|tab| tab.id == tab_id) else {
            return false;
        };
        let url = &tab.url;
        let title = &tab.title;
        if title.is_empty() {
            return false;
        }

        let mut updated = false;
        for entry in self.history.iter_mut().chain(self.bookmarks.iter_mut()) {
            if same_page_url(&entry.url, url) && entry.title != *title {
                entry.title.clone_from(title);
                updated = true;
            }
        }
        updated
    }

    pub fn active_index(&self) -> usize {
        self.tabs
            .iter()
            .position(|tab| tab.id == self.active_id)
            .unwrap_or(0)
    }
}

fn same_page_url(left: &str, right: &str) -> bool {
    fn without_root_slash(url: &str) -> &str {
        let Some((_, authority_and_path)) = url.split_once("://") else {
            return url;
        };
        if authority_and_path
            .split_once('/')
            .is_some_and(|(_, path)| path.is_empty())
        {
            &url[..url.len() - 1]
        } else {
            url
        }
    }

    without_root_slash(left) == without_root_slash(right)
}

pub fn resolve_address(input: &str, search_template: &str) -> String {
    let query = input.trim();
    if query.is_empty() {
        return String::new();
    }
    if starts_with_ascii_case_insensitive(query, "http://")
        || starts_with_ascii_case_insensitive(query, "https://")
        || starts_with_ascii_case_insensitive(query, "about:")
    {
        return query.to_owned();
    }
    if is_loopback_address(query) {
        return format!("http://{query}");
    }
    if query.contains(' ') || !query.contains('.') {
        return search_template.replace("%s", &encode_query(query));
    }
    format!("https://{query}")
}

fn is_loopback_address(query: &str) -> bool {
    let authority = query.split(['/', '?', '#']).next().unwrap_or(query);
    let host = if let Some(address) = authority.strip_prefix('[') {
        match address.find(']') {
            Some(end) => &address[..end],
            None => return false,
        }
    } else {
        authority.split(':').next().unwrap_or("")
    };
    host.eq_ignore_ascii_case("localhost")
        || host
            .parse::<std::net::IpAddr>()
            .is_ok_and(|address| address.is_loopback())
}

fn starts_with_ascii_case_insensitive(value: &str, prefix: &str) -> bool {
    value
        .get(..prefix.len())
        .is_some_and(|start| start.eq_ignore_ascii_case(prefix))
}

fn encode_query(query: &str) -> String {
    let mut encoded = String::with_capacity(query.len());
    for byte in query.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'*' => {
                encoded.push(byte as char)
            }
            b' ' => encoded.push('+'),
            _ => {
                const HEX: &[u8; 16] = b"0123456789ABCDEF";
                encoded.push('%');
                encoded.push(HEX[(byte >> 4) as usize] as char);
                encoded.push(HEX[(byte & 0x0F) as usize] as char);
            }
        }
    }
    encoded
}

#[cfg(test)]
mod tests {
    use super::{resolve_address, BrowserState, Entry};

    const SEARCH: &str = "https://search.example/?q=%s";

    #[test]
    fn restores_at_least_one_tab_and_clamps_the_selection() {
        let empty = BrowserState::restore(vec![], 9, vec![], vec![], 0);
        assert_eq!(empty.tabs.len(), 1);
        let state = BrowserState::restore(
            vec!["https://one.test".into(), "https://two.test".into()],
            9,
            vec![],
            vec![],
            99,
        );
        assert_eq!(state.current().unwrap().url, "https://two.test");
        assert_eq!(state.search_engine, 3);
    }

    #[test]
    fn restore_deduplicates_history_preserving_newest_entry() {
        let state = BrowserState::restore(
            vec![],
            0,
            vec![],
            vec![
                Entry {
                    title: "Newest".into(),
                    url: "https://one.test".into(),
                },
                Entry {
                    title: "Second".into(),
                    url: "https://two.test".into(),
                },
                Entry {
                    title: "Older".into(),
                    url: "https://one.test/".into(),
                },
            ],
            0,
        );
        assert_eq!(state.history.len(), 2);
        assert_eq!(state.history[0].title, "Newest");
        assert_eq!(state.history[1].url, "https://two.test");
    }

    #[test]
    fn root_slash_variants_share_bookmark_and_history_identity() {
        let entry = Entry {
            title: "Old".into(),
            url: "https://one.test/".into(),
        };
        let mut state = BrowserState::restore(
            vec!["https://one.test".into()],
            0,
            vec![entry.clone()],
            vec![entry],
            0,
        );
        assert!(!state.add_bookmark());

        let tab_id = state.active_id.clone();
        state.record_visit(&tab_id);
        assert_eq!(state.history.len(), 1);

        state.tabs[0].title = "Updated".into();
        assert!(state.update_saved_titles(&tab_id));
        assert_eq!(state.history[0].title, "Updated");
        assert_eq!(state.bookmarks[0].title, "Updated");
    }

    #[test]
    fn selecting_unknown_tab_does_not_change_current() {
        let mut state =
            BrowserState::restore(vec!["https://one.test".into()], 0, vec![], vec![], 0);
        let id = state.active_id.clone();
        assert!(!state.select_tab("missing"));
        assert_eq!(state.active_id, id);
    }

    #[test]
    fn closing_active_tab_selects_neighbor_and_never_leaves_zero_tabs() {
        let mut state =
            BrowserState::restore(vec!["one".into(), "two".into()], 0, vec![], vec![], 0);
        let active = state.active_id.clone();
        state.close_tab(&active);
        assert_eq!(state.tabs.len(), 1);
        assert_eq!(state.current().unwrap().url, "two");
        let last = state.active_id.clone();
        state.close_tab(&last);
        assert_eq!(state.tabs.len(), 1);
        assert_eq!(state.current().unwrap().url, "");
    }

    #[test]
    fn bookmarks_are_deduplicated_and_history_is_bounded() {
        let mut state =
            BrowserState::restore(vec!["https://site.test".into()], 0, vec![], vec![], 0);
        let tab = state.active_id.clone();
        state.tabs[0].title = "Site".into();
        assert!(state.add_bookmark());
        assert!(!state.add_bookmark());
        for index in 0..102 {
            state.tabs[0].url = format!("https://{index}.test");
            state.record_visit(&tab);
        }
        assert_eq!(state.history.len(), 100);
        assert_eq!(state.history[0].url, "https://101.test");
        assert_eq!(
            state.bookmarks,
            vec![Entry {
                title: "Site".into(),
                url: "https://site.test".into()
            }]
        );
    }

    #[test]
    fn revisiting_a_history_entry_moves_it_to_the_front_without_duplicates() {
        let mut state =
            BrowserState::restore(vec!["https://first.test".into()], 0, vec![], vec![], 0);
        let tab_id = state.active_id.clone();
        state.record_visit(&tab_id);
        state.tabs[0].url = "https://second.test".into();
        state.record_visit(&tab_id);
        state.tabs[0].url = "https://first.test".into();
        state.record_visit(&tab_id);

        assert_eq!(state.history.len(), 2);
        assert_eq!(state.history[0].url, "https://first.test");
        assert_eq!(state.history[1].url, "https://second.test");
    }

    #[test]
    fn page_title_updates_existing_bookmark_and_history_entries() {
        let url = "https://site.test";
        let mut state = BrowserState::restore(vec![url.into()], 0, vec![], vec![], 0);
        let tab_id = state.active_id.clone();
        state.record_visit(&tab_id);
        assert!(state.add_bookmark());
        state.tabs[0].title = "Readable title".into();

        assert!(state.update_saved_titles(&tab_id));
        assert_eq!(state.history[0].title, "Readable title");
        assert_eq!(state.bookmarks[0].title, "Readable title");
        assert!(!state.update_saved_titles(&tab_id));
    }

    #[test]
    fn address_resolution_preserves_supported_schemes_and_encodes_search() {
        assert_eq!(
            resolve_address("  https://example.test/path ", SEARCH),
            "https://example.test/path"
        );
        assert_eq!(
            resolve_address("localhost:3000", SEARCH),
            "http://localhost:3000"
        );
        assert_eq!(
            resolve_address("private & café", SEARCH),
            "https://search.example/?q=private+%26+caf%C3%A9"
        );
        assert_eq!(resolve_address("", SEARCH), "");
    }
}
