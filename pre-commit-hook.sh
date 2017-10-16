#!/bin/sh
illegalPatterns=(
    '\bOATS'                    # FINRA-ism OATS project
    '\bHUB'                     # FINRA-ism HUB project
    'AKIA[a-zA-Z0-9]'           # Possible AWS access key
    '4652'                      # Part of FINRA account number in DEV VPC
    '1422'                      # Part of FINRA account number in QA VPC
    '5101'                      # Part of FINRA account number in PROD VPC
    '[^:</]datamgt[^:]'         # Possible part of AWS resource name. Some matches exist in an XSD which is OK
    '[^/]datamgmt[^/]'          # Possible part of AWS resource name. Some matches exist in a file path, which is OK
    'EMR_scheduler'             # Application instance role
    '[^/]Datamanagement[^/]'    # Some matches exist in a file path, which is OK
    'priv_aws'                  # FINRA IAM user name infix
    'nasd'                      # Possible NASDCORP domain name
    'corp[^o]'                  # Possible NASDCORP domain name, "Corpo" exists in swagger i18n
    'sg-[a-f0-9A-F]{8}'         # Security group pattern
    '[^_\.]subnet_[a-zA-Z]'     # FINRA subnet name prefix
    'saml'                      # Possible part of a FINRA entity name
    '[^w][^\.@]finra\.org'      # Possible FINRA domain name, but ignore FINRA public website and e-mails
    # The above pattern is not perfect. For example, a string "aaw.finra.org" would not be flagged as illegal, but this should catch most cases
    # If we can find a way to improve it such that ONLY www.finra.org and @finra.org is ignored, that would be great.
    '\btdate\b'                 # FINRA-ism trade date
    '\bnyx\b'                   # FINRA-ism data provider
    'mkt_cntr'                  # FINRA-ism partition key
    'dm-'                       # Possible reference to databridge
    'request\smanager'          # FINRA-ism RM project
    'datamgt-external'          # FINRA bucket name
)

for illegalPattern in ${illegalPatterns[@]}
do
    matchedLines=$(git diff --cached --diff-filter=AM --no-color | grep '^+' | grep -i ${illegalPattern})
    if [ "$matchedLines" ]
    then
        echo 'Illegal pattern'
        echo ${illegalPattern}
        echo 'found in:'
        echo $matchedLines
        exit 1
    fi
done