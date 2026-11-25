use anyhow::Result;
use clap::Parser;

use crate::boot_patch::{BootPatchArgs, BootRestoreArgs};
use crate::lkm_image::BootPatchV2Args;
use crate::{apk_sign, defs};

/// MuSu Next cli for non-android
#[derive(Parser, Debug)]
#[command(author, version = defs::VERSION_NAME, about, long_about = None)]
struct Args {
    #[command(subcommand)]
    command: Commands,
}

#[derive(clap::Subcommand, Debug)]
enum Commands {
    /// Patch boot or init_boot images to apply MuSu Next
    BootPatch(BootPatchArgs),

    /// Restore boot or init_boot images patched by MuSu Next
    BootRestore(BootRestoreArgs),

    /// Patch MuSu Next into a boot image
    ///
    /// Always operates on a boot image; never selects init_boot or vendor_boot.
    BootPatchV2(BootPatchV2Args),

    /// Get apk size and hash
    GetSign {
        /// apk path
        apk: String,
    },

    /// show supported kmi versions
    SupportedKmis,

    /// Backup and restore module states, root grants and builtin switches
    Snapshot {
        #[command(subcommand)]
        command: Snapshot,
    },

    /// Detect what an unauthorized app can see (drops privileges before probing)
    Envcheck,
}

#[derive(clap::Subcommand, Debug)]
enum Snapshot {
    /// Create a new snapshot archive
    Create,
    /// List stored snapshots
    List,
    /// Restore from a snapshot (writes a pre-restore snapshot first)
    Restore {
        /// snapshot archive path
        path: String,
    },
    /// Delete a snapshot archive
    Delete {
        /// snapshot archive path
        path: String,
    },
}

pub fn run() -> Result<()> {
    env_logger::init();

    let cli = Args::parse();

    log::info!("command: {:?}", cli.command);

    let result = match cli.command {
        Commands::GetSign { apk } => {
            let sign = apk_sign::get_apk_signature(&apk)?;
            println!("size: {:#x}, hash: {}", sign.0, sign.1);
            Ok(())
        }

        Commands::BootPatch(boot_patch) => crate::boot_patch::patch(boot_patch),

        Commands::BootRestore(boot_restore) => crate::boot_patch::restore(boot_restore),

        Commands::BootPatchV2(patch) => crate::lkm_image::patch_boot(&patch),

        Commands::SupportedKmis => {
            let kmi = crate::assets::list_supported_kmi();
            for kmi in &kmi {
                println!("{kmi}");
            }
            Ok(())
        }
        Commands::Snapshot { command } => {
            use crate::jsoncmd::emit;
            match command {
                Snapshot::Create => emit(&crate::snapshot::cmd_create()),
                Snapshot::List => emit(&crate::snapshot::cmd_list()),
                Snapshot::Restore { path } => emit(&crate::snapshot::cmd_restore(&path)),
                Snapshot::Delete { path } => emit(&crate::snapshot::cmd_delete(&path)),
            }
        }
        Commands::Envcheck => crate::jsoncmd::emit(&crate::envcheck::run()),
    };

    if let Err(e) = &result {
        log::error!("Error: {e:?}");
    }
    result
}
