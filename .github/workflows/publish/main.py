import sys
import beta
import stable
import helpers

if __name__ == "__main__":
    release_type = sys.argv[1]
    workspace_dir = sys.argv[2]

    version_code = helpers.parse_project_version_name(workspace_dir)
    print(f'version_code: {version_code}')

    if release_type == "beta":
        beta.publish_beta(workspace_dir, version_code)
    elif release_type == "stable":
        stable.publish_stable(workspace_dir, version_code)
    else:
        print(f"Unknown release_type: {release_type}")
        exit(-1)
    
    print('Success!')
