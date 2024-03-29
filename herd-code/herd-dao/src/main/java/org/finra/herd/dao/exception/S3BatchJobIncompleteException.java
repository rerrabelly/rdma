/*
 * Copyright 2015 herd contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.finra.herd.dao.exception;

import com.amazonaws.services.s3control.model.DescribeJobResult;

public class S3BatchJobIncompleteException extends RuntimeException
{
    DescribeJobResult jobDescriptor;

    public S3BatchJobIncompleteException(DescribeJobResult jobDescriptor)
    {
        super();
        this.jobDescriptor = jobDescriptor;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (getJobDescriptor() != null)
        {
            sb.append("JobDescriptor: ").append(getJobDescriptor());
        }
        sb.append("}");
        return sb.toString();
    }

    public DescribeJobResult getJobDescriptor()
    {
        return jobDescriptor;
    }
}


