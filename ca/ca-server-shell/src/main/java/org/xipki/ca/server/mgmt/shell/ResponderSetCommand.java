/*
 * Copyright 2014 xipki.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 */

package org.xipki.ca.server.mgmt.shell;

import java.security.cert.X509Certificate;

import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.xipki.ca.server.mgmt.CmpResponderEntry;
import org.xipki.security.api.PasswordResolver;
import org.xipki.security.api.SecurityFactory;
import org.xipki.security.common.IoCertUtil;

@Command(scope = "ca", name = "responder-set", description="Set responder")
public class ResponderSetCommand extends CaCommand
{
    @Option(name = "-signerType",
            description = "Required. Type of the responder signer",
            required = true)
    protected String            signerType;

    @Option(name = "-signerConf",
            description = "Conf of the responder signer")
    protected String            signerConf;

    @Option(name = "-cert",
            description = "Requestor certificate, in form of 'file:<path> or base64:<content>")
    protected String            certFile;

    private SecurityFactory securityFactory;
    private PasswordResolver passwordResolver;

    @Override
    protected Object doExecute() throws Exception
    {
        CmpResponderEntry entry = new CmpResponderEntry();
        X509Certificate signerCert = null;
        if(certFile != null)
        {
            signerCert = IoCertUtil.parseCert(certFile);
            entry.setCertificate(signerCert);
        }
        entry.setType(signerType);

        if("PKCS12".equalsIgnoreCase(signerType) || "JKS".equalsIgnoreCase(signerType))
        {
            signerConf = ShellUtil.replaceFileInSignerConf(signerConf);
        }

        entry.setConf(signerConf);

        // check whether we can initialize the signer
        securityFactory.createSigner(signerType, signerConf, signerCert, passwordResolver);

        caManager.setCmpResponder(entry);

        return null;
    }


    public void setSecurityFactory(SecurityFactory securityFactory)
    {
        this.securityFactory = securityFactory;
    }

    public void setPasswordResolver(PasswordResolver passwordResolver)
    {
        this.passwordResolver = passwordResolver;
    }
}
