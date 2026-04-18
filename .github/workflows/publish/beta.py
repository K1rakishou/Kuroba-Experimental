import os
import helpers
import github

ApkNames = [
    "KurobaEx-beta-arm64-v8a.apk",
    "KurobaEx-beta-x86_64.apk",
    "KurobaEx-beta-armeabi-v7a.apk",
    "KurobaEx-beta-x86.apk",
    "KurobaEx-beta.apk"
]

def publish_beta(token, workspace_dir, version_code: helpers.VersionCode):
    print(f"Publishing beta release")

    tag_name = helpers.get_new_beta_tag_name(version_code)
    if (len(tag_name) == 0):
        print("Failed to get the release tag.")
        exit(-1)

    latest_release_commit_hash = github.get_latest_release_commit_hash(helpers.RepoName)
    commits = helpers.get_commits_since(latest_release_commit_hash)

    print(f'tag_name: {tag_name}')
    print(f'commits:\n{commits}')

    release_name = f'KurobaEx-beta release {tag_name}'
    body = commits
    assets_path = workspace_dir + helpers.BetaApkRelativePath
    
    github.create_github_release(token, helpers.RepoName, tag_name, release_name, body, assets_path, ApkNames, True)

