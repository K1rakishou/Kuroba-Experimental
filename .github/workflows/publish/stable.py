import github
import helpers

ApkNames = [
    "KurobaEx-arm64-v8a.apk",
    "KurobaEx-x86_64.apk",
    "KurobaEx-armeabi-v7a.apk",
    "KurobaEx-x86.apk",
    "KurobaEx.apk"
]
def publish_stable(token, workspace_dir, version_code: helpers.VersionCode):
    print(f"Publishing stable release")
    
    latest_release_commit_hash = github.get_latest_release_commit_hash(helpers.StableRepoName)
    commits = helpers.get_commits_since(latest_release_commit_hash)
    tag_name = f'v{version_code.major}.{version_code.minor}.{version_code.patch}-release'
    
    print(f'tag_name: {tag_name}')
    print(f'commits:\n{commits}')

    release_name = f'KurobaEx release {tag_name}'
    body = commits
    asset_path = workspace_dir + helpers.StableApkRelativePath
    
    github.create_github_release(token, helpers.StableRepoName, tag_name, release_name, body, asset_path, ApkNames)

