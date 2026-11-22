use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

fn resolve_address(input: &str, search_template: &str) -> String {
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

    if query.contains(' ') || !query.contains('.') {
        return search_template.replace("%s", &encode_query(query));
    }

    format!("https://{query}")
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
                encoded.push(byte as char);
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

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_RustBrowserCore_resolveAddress(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
    search_template: JString,
) -> jstring {
    let input: String = match env.get_string(&input) {
        Ok(value) => value.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let template: String = match env.get_string(&search_template) {
        Ok(value) => value.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    match env.new_string(resolve_address(&input, &template)) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[cfg(test)]
mod tests {
    use super::resolve_address;

    const TEMPLATE: &str = "https://search.example/?q=%s";

    #[test]
    fn preserves_explicit_schemes_and_about_pages() {
        assert_eq!(
            resolve_address(" https://example.com ", TEMPLATE),
            "https://example.com"
        );
        assert_eq!(
            resolve_address("HTTPS://example.com", TEMPLATE),
            "HTTPS://example.com"
        );
        assert_eq!(resolve_address("about:blank", TEMPLATE), "about:blank");
        assert_eq!(
            resolve_address("http://example.com", TEMPLATE),
            "http://example.com"
        );
    }

    #[test]
    fn treats_domains_as_https_addresses() {
        assert_eq!(
            resolve_address("example.com/path", TEMPLATE),
            "https://example.com/path"
        );
    }

    #[test]
    fn encodes_search_terms_as_form_data() {
        assert_eq!(
            resolve_address("privacy & café", TEMPLATE),
            "https://search.example/?q=privacy+%26+caf%C3%A9"
        );
    }

    #[test]
    fn blank_input_stays_blank() {
        assert_eq!(resolve_address("   ", TEMPLATE), "");
    }
}
