//! End-to-end tests for snapshot commands (host-side, real archive I/O).

use super::*;

fn profile(key: &str, uid: i64) -> Value {
    json!({
        "key": key,
        "curr_uid": uid,
        "allow_su": true,
        "root": {
            "use_default": false,
            "template": "",
            "uid": 0,
            "gid": 0,
            "groups": [0],
            "capabilities": {"effective": 1, "permitted": 2, "inheritable": 3},
            "context": "u:r:ksu:s0",
            "namespaces": 0,
            "flags": 1,
        },
        "non_root": {"use_default": true, "umount_modules": false},
    })
}

#[test]
fn allowlist_roundtrip() {
    let source = vec![profile("org.example", 10123), profile("org.other", 10124)];
    data::write_profiles(&source).expect("write allowlist");
    let parsed = data::read_profiles().expect("read allowlist");
    assert_eq!(parsed.len(), 2);
    assert_eq!(parsed[0]["key"], "org.example");
    assert_eq!(parsed[0]["curr_uid"], 10123);
    assert_eq!(parsed[0]["root"]["context"], "u:r:ksu:s0");
    assert_eq!(parsed[1]["curr_uid"], 10124);
    // Second write must reproduce byte-identical raw records.
    data::write_profiles(&parsed).expect("rewrite allowlist");
    assert_eq!(data::read_profiles().unwrap(), parsed);
}

#[test]
fn snapshot_roundtrip() {
    // Arrange a small synthetic environment under the real on-device paths.
    fs::create_dir_all(data::SNAPSHOT_DIR).unwrap();
    for entry in fs::read_dir(data::SNAPSHOT_DIR).unwrap().flatten() {
        let _ = fs::remove_file(entry.path());
    }
    let module = Path::new(data::MODULE_DIR).join("test.module");
    fs::create_dir_all(&module).unwrap();
    fs::write(module.join("module.prop"), "id=test.module\nname=Test\nversion=1\n").unwrap();
    let marker = module.join("disable");
    fs::write(&marker, "").unwrap();
    data::write_profiles(&[profile("org.example", 10123)]).unwrap();
    let state = Path::new(data::BUILTIN_STATE_PATH);
    fs::create_dir_all(state.parent().unwrap()).unwrap();
    fs::write(
        state,
        json!({"schema": 1, "components": {"zygisk": {"enabled": true, "note": "keep"}}})
            .to_string(),
    )
    .unwrap();

    let expected_modules = data::read_modules().unwrap().len();
    let created = cmd_create();
    assert_eq!(created.code, 0, "{:?}", created.json);
    assert_eq!(created.json["ok"], true);
    assert_eq!(created.json["modules"], expected_modules);
    assert_eq!(created.json["profiles"], 1);
    assert_eq!(created.json["builtins"], 2);
    let path = created.json["path"].as_str().unwrap().to_string();
    let pre = fs::read_dir(data::SNAPSHOT_DIR)
        .unwrap()
        .flatten()
        .filter_map(|e| e.file_name().to_str().map(str::to_string))
        .filter(|name| name.contains(".pre-restore"))
        .count();
    assert_eq!(pre, 0, "create must not write a pre-restore snapshot");

    let listed = cmd_list();
    assert_eq!(listed.code, 0);
    assert!(listed.json["snapshots"].as_array().unwrap().len() >= 1);

    // Mutate everything, then restore.
    fs::remove_file(&marker).unwrap();
    data::write_profiles(&[]).unwrap();
    let mut value: Value = serde_json::from_slice(&fs::read(state).unwrap()).unwrap();
    value["components"]["zygisk"]["enabled"] = json!(false);
    fs::write(state, value.to_string()).unwrap();

    let restored = cmd_restore(&path);
    assert_eq!(restored.code, 0, "{:?}", restored.json);
    assert_eq!(restored.json["need_reboot"], true);
    assert_eq!(restored.json["restored"]["modules"], expected_modules);
    assert_eq!(restored.json["restored"]["profiles"], 1);
    assert_eq!(restored.json["restored"]["builtins"], 2);
    assert!(marker.exists(), "module must be disabled again");
    assert_eq!(data::read_profiles().unwrap().len(), 1);
    let after: Value = serde_json::from_slice(&fs::read(state).unwrap()).unwrap();
    assert_eq!(after["components"]["zygisk"]["enabled"], true);
    assert_eq!(after["components"]["zygisk"]["note"], "keep");

    assert!(
        fs::read_dir(data::SNAPSHOT_DIR)
            .unwrap()
            .flatten()
            .any(|e| e.file_name().to_string_lossy().contains(".pre-restore")),
        "restore must leave a pre-restore snapshot"
    );

    let deleted = cmd_delete(&path);
    assert_eq!(deleted.code, 0);
    assert!(!Path::new(&path).exists());
}

#[test]
fn traversal_is_rejected() {
    let outcome = cmd_delete("../../etc/passwd");
    assert_eq!(outcome.code, 1);
    assert_eq!(outcome.json["ok"], false);
}
