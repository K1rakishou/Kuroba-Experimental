import os
import re
import helpers
import github

def publish_beta(workspace_dir, version_code: helpers.VersionCode):
    print(f"Publishing beta release")

    tag_name = helpers.get_new_beta_tag_name(version_code)
    if (len(tag_name) == 0):
        print("Failed to get the release tag.")
        exit(-1)

    latest_release_commit_hash = github.get_latest_release_commit_hash('K1rakishou/Kuroba-Experimental')
    if (len(latest_release_commit_hash) == 0):
         print("Failed to get latest release commit hash.")
         exit(-1)

    commits = helpers.get_commits_since(latest_release_commit_hash)

    print(f'tag_name: {tag_name}')
    print(f'commits:\n{commits}')

    repo = 'K1rakishou/Kuroba-Experimental-beta'
    release_name = f'KurobaEx-beta release {tag_name}'
    body = commits
    asset_path = workspace_dir + "/Kuroba/app/build/outputs/apk/beta/release/KurobaEx-beta.apk"
    
    token = os.getenv('PAT') or ""
    if (len(token) == 0):
        print("Token is empty.")
        exit(-1)

    github.create_github_release(token, repo, tag_name, release_name, body, asset_path)

