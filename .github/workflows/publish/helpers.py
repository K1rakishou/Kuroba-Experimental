import os
import subprocess
import re
import github

BetaRepoName = 'K1rakishou/Kuroba-Experimental-beta'
StableRepoName = 'K1rakishou/Kuroba-Experimental'

BetaTagPattern = r'v(\d+?)\.(\d{1,2})\.(\d{1,2})\.(\d+)-beta$'
VersionNamePattern = r'versionName\s+\"v(\d+)\.(\d{1,2})\.(\d{1,2})\"'

BetaApkRelativePath = "/Kuroba/app/build/outputs/apk/beta/release"
StableApkRelativePath = "/Kuroba/app/build/outputs/apk/stable/release"

class BuildCreationError(Exception):
    pass

class VersionCode:
    def __init__(self, major, minor, patch):
        self.major = major
        self.minor = minor
        self.patch = patch
        
    def __str__(self):
            return f"VersionCode(major: {self.major}, minor: {self.minor}, patch: {self.patch})"

def get_commits_since(commit_hash, repo_path=None):
    if repo_path:
        os.chdir(repo_path)

    cmd = ["git", "log",f"{commit_hash}..HEAD","--pretty=format:%B%x1e"]
    all_commits = ""

    output = subprocess.check_output(cmd, stderr=subprocess.STDOUT, text=True)
    commits = output.strip().split('\x1e')
    commit_counter = 0

    for commit in commits:
        commit = commit.strip()
        if not commit:
            continue

        if commit.startswith("Merge"):
            continue

        if commit_counter > 10:
            break

        all_commits += f"- {commit}\n\n"
        commit_counter += 1

    return all_commits

def get_new_stable_tag_name(version_code: VersionCode):
    print(f"get_new_tag_name() is_stable: true, version_code: {version_code}")
    new_stable_tag_name = f"v{version_code.major}.{version_code.minor}.{version_code.patch}-release"
    print(f"get_new_tag_name() new_stable_tag_name: {new_stable_tag_name}")
    return new_stable_tag_name
    

def get_new_beta_tag_name(version_code: VersionCode):
    tag_name = github.get_latest_release_tag(BetaRepoName)
    print(f"get_new_tag_name() is_stable: false, tag_name: {tag_name}")

    beta_major_version = -1
    beta_minor_version = -1
    beta_patch_version = -1
    beta_increment_version = -1

    beta_pattern_match = re.search(BetaTagPattern, tag_name)
    if beta_pattern_match:
        groups = beta_pattern_match.groups()
        beta_major_version = int(groups[0])
        beta_minor_version = int(groups[1])
        beta_patch_version = int(groups[2])
        beta_increment_version = int(groups[3])
    else:
        raise BuildCreationError(f"Failed to parse latest beta tag: {tag_name}")

    if beta_major_version < 0 or beta_minor_version < 0 or beta_patch_version < 0 or beta_increment_version < 0:
        raise BuildCreationError(f"Failed to parse latest beta tag: {tag_name}")
    
    # The version code of the last tag is still the same as the version code from the build.gradle.
    # Just increase the "increment" version.
    if beta_major_version == version_code.major and\
        beta_minor_version == version_code.minor and\
        beta_patch_version == version_code.patch:

        new_beta_increment_version = beta_increment_version + 1
        new_beta_tag_name = f"v{beta_major_version}.{beta_minor_version}.{beta_patch_version}.{new_beta_increment_version}-beta"

        print(f"get_new_tag_name() (No version code changes detected) new_beta_tag_name: {new_beta_tag_name}")
        return new_beta_tag_name

    # Version code was changed. Use the major/minor/patch parts from VersionCode and set "increment" to 0
    new_beta_tag_name = f"v{version_code.major}.{version_code.minor}.{version_code.patch}.0-beta"
    print(f"get_new_tag_name() (Version code change detected!) new_beta_tag_name: {new_beta_tag_name}")

    return new_beta_tag_name
    


def parse_project_version_name(workspace_dir):
    build_gradle_file_path = workspace_dir + "/Kuroba/app/build.gradle"
    print(f'parse_project_version_name() build_gradle_file_path: {build_gradle_file_path}')
    
    with open(build_gradle_file_path, "rb") as file:
        build_gradle_file_contents = file.read().decode("utf-8")
        
        version_name_match = re.search(VersionNamePattern, build_gradle_file_contents)
        if version_name_match:
            groups = version_name_match.groups()
            print(f'groups: {groups}')
            
            major_version = int(groups[0])
            minor_version = int(groups[1])
            patch_version = int(groups[2])

            return VersionCode(major_version, minor_version, patch_version)
        else:
            raise BuildCreationError(f"Failed to parse versionName in build.gradle")
